// Boots real Paper and Folia servers across the supported range and drives DonutRTP with mineflayer bots.
// Every version runs the same scenario (1.x upgrade, menu, warmup, cancels, Nether and End, landing protection,
// group and solo zones, WorldGuard and GriefPrevention avoidance, costs, permissions, placeholders, admin commands)
// and the console is scanned for plugin errors.
//
// Usage (from the repository root, after building the plugin and the fixture, see README.md):
//   node tools/smoke/run.mjs                 # the whole matrix
//   node tools/smoke/run.mjs paper-26.3      # selected targets (project-version)
//
// Downloads (JDKs, server jars, plugins) are cached in $RTPLAB_HOME (default ~/.cache/donutrtp-smoke). Server jars
// and JDKs already downloaded by the DonutOrders lab are reused.
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mineflayer from 'mineflayer';
import nbt from 'prismarine-nbt';
import { scenario } from './scenario.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const lab = process.env.RTPLAB_HOME || path.join(os.homedir(), '.cache', 'donutrtp-smoke');
const cache = path.join(lab, 'cache');
const shared = path.join(os.homedir(), '.cache', 'donutorders-smoke', 'cache');
const PORT = Number(process.env.RTPLAB_PORT || 25598);

// Bots speak the newest protocol mineflayer knows; newer servers get ViaVersion + ViaBackwards.
export const MATRIX = [
  { project: 'paper', version: '1.20.1', java: 17, bot: '1.20.1', economy: 'Vault' },
  { project: 'paper', version: '1.20.4', java: 17, bot: '1.20.4', economy: 'VaultUnlocked' },
  { project: 'paper', version: '1.20.6', java: 21, bot: '1.20.6', economy: 'Vault' },
  { project: 'paper', version: '1.21.1', java: 21, bot: '1.21.1', economy: 'VaultUnlocked' },
  { project: 'paper', version: '1.21.4', java: 21, bot: '1.21.4', economy: 'Vault' },
  { project: 'paper', version: '1.21.8', java: 21, bot: '1.21.8', economy: 'VaultUnlocked' },
  { project: 'paper', version: '1.21.11', java: 21, bot: '1.21.11', economy: 'Vault' },
  { project: 'paper', version: '26.1.2', java: 25, bot: '26.1', economy: 'VaultUnlocked', via: true },
  { project: 'paper', version: '26.2', java: 25, bot: '26.1', economy: 'Vault', via: true },
  { project: 'paper', version: '26.3', java: 25, bot: '26.1', economy: 'VaultUnlocked', via: true },
  { project: 'folia', version: '1.20.4', java: 17, bot: '1.20.4', economy: 'VaultUnlocked' },
  { project: 'folia', version: '1.21.4', java: 21, bot: '1.21.4', economy: 'VaultUnlocked' },
  { project: 'folia', version: '1.21.11', java: 21, bot: '1.21.11', economy: 'VaultUnlocked' },
  { project: 'folia', version: '26.3', java: 25, bot: '26.1', economy: 'VaultUnlocked', via: true },
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const AGENT = { 'User-Agent': 'NightBeam-DonutRTP-smoke' };

async function fetchJson(url) {
  const response = await fetch(url, { headers: AGENT });
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  return response.json();
}

async function download(url, file) {
  if (fs.existsSync(file)) return file;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const response = await fetch(url, { headers: AGENT });
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  fs.writeFileSync(file + '.part', Buffer.from(await response.arrayBuffer()));
  fs.renameSync(file + '.part', file);
  return file;
}

/** A file from this lab's cache, else the DonutOrders lab's cache, else null. */
function cached(...parts) {
  for (const root of [cache, shared]) {
    const file = path.join(root, ...parts);
    if (fs.existsSync(file)) return file;
  }
  return null;
}

export async function jdk(major) {
  const known = cached('jdk', String(major), 'bin', 'java');
  if (known) return known;
  const dir = path.join(cache, 'jdk', String(major));
  const archive = await download(`https://api.adoptium.net/v3/binary/latest/${major}/ga/linux/x64/jdk/hotspot/normal/eclipse`, dir + '.tar.gz');
  fs.mkdirSync(dir, { recursive: true });
  await run('tar', ['-xzf', archive, '-C', dir, '--strip-components=1']);
  fs.rmSync(archive);
  return path.join(dir, 'bin', 'java');
}

export async function serverJar(project, version) {
  const known = cached('servers', `${project}-${version}.jar`);
  if (known) return known;
  const build = await fetchJson(`https://fill.papermc.io/v3/projects/${project}/versions/${version}/builds/latest`);
  return download(build.downloads['server:default'].url, path.join(cache, 'servers', `${project}-${version}.jar`));
}

/**
 * A release of a Modrinth project for a loader, or null when there is none. Without a game version it is the newest
 * release. With one it is the oldest release for that version: the first build made for it, which runs on the Java of
 * its time (newer builds of WorldEdit need Java 25 even when they list older game versions).
 */
export async function modrinth(project, loader = 'paper', gameVersion = null) {
  const name = `${project}-${loader}${gameVersion ? '-' + gameVersion : ''}.jar`;
  const known = cached('plugins', name);
  if (known) return known;
  let query = `loaders=${encodeURIComponent(JSON.stringify([loader]))}`;
  if (gameVersion) query += `&game_versions=${encodeURIComponent(JSON.stringify([gameVersion]))}`;
  const versions = await fetchJson(`https://api.modrinth.com/v2/project/${project}/version?${query}`);
  const releases = versions.filter((v) => v.version_type === 'release');
  const release = gameVersion ? releases.at(-1) : releases[0] || versions[0];
  if (!release) return null;
  return download(release.files.find((f) => f.primary)?.url || release.files[0].url, path.join(cache, 'plugins', name));
}

export async function economyJar(name) {
  if (name === 'Vault') {
    return cached('plugins', 'Vault-1.7.3.jar')
      || download('https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar', path.join(cache, 'plugins', 'Vault-1.7.3.jar'));
  }
  return cached('plugins', 'VaultUnlocked.jar') || modrinth('vaultunlocked');
}

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: 'inherit', ...options });
    child.on('exit', (code) => (code === 0 ? resolve() : reject(new Error(`${command} exited with ${code}`))));
  });
}

export function pluginJar() {
  if (process.env.PLUGIN_JAR) return process.env.PLUGIN_JAR;
  const libs = path.join(repo, 'build', 'libs');
  const jar = fs.existsSync(libs) && fs.readdirSync(libs).filter((f) => /^DonutRTP-\d.*\.jar$/.test(f)).sort().at(-1);
  if (!jar) throw new Error('Build the plugin first: ./gradlew build');
  return path.join(libs, jar);
}

/** One running server: stdin for console commands, and a line log that waitFor() scans. */
export class Server {
  constructor(dir, java, jar) {
    this.dir = dir;
    this.java = java;
    this.jar = jar;
    this.lines = [];
    this.cursor = 0;
    this.waiters = [];
  }

  start() {
    const log = fs.createWriteStream(path.join(this.dir, 'console.log'));
    this.process = spawn(this.java, ['-Xms1G', '-Xmx3G', ...(process.env.RTPLAB_DEBUG ? ['-Ddonutrtp.debug=true'] : []), '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Dfile.encoding=UTF-8',
      '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', this.jar, '--nogui'], { cwd: this.dir });
    let buffer = '';
    const onData = (chunk) => {
      log.write(chunk);
      buffer += chunk.toString('utf8');
      let index;
      while ((index = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, index).replace(/\x1b\[[0-9;]*m/g, '');
        buffer = buffer.slice(index + 1);
        this.lines.push(line);
        this.waiters = this.waiters.filter((waiter) => !waiter.test(line));
      }
    };
    this.process.stdout.on('data', onData);
    this.process.stderr.on('data', onData);
    this.exited = new Promise((resolve) => this.process.on('exit', resolve));
  }

  /** Resolves with the first line after the cursor that matches. */
  waitFor(pattern, timeout = 30_000, label = String(pattern)) {
    for (let i = this.cursor; i < this.lines.length; i++) {
      const match = this.lines[i].match(pattern);
      if (match) {
        this.cursor = i + 1;
        return Promise.resolve(match);
      }
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters = this.waiters.filter((waiter) => waiter !== entry);
        reject(new Error(`timed out waiting for ${label}`));
      }, timeout);
      const entry = {
        test: (line) => {
          const match = line.match(pattern);
          if (!match) return false;
          clearTimeout(timer);
          this.cursor = this.lines.length;
          resolve(match);
          return true;
        },
      };
      this.waiters.push(entry);
    });
  }

  /** Whether any line so far matches (no waiting, cursor unchanged). */
  seen(pattern) {
    return this.lines.some((line) => pattern.test(line));
  }

  mark() {
    this.cursor = this.lines.length;
  }

  command(line) {
    this.process.stdin.write(line + '\n');
  }

  /** Runs a fixture command and returns the text after "[RTPLAB] ". */
  async lab(line, timeout = 20_000) {
    this.mark();
    this.command('rtplab ' + line);
    const word = line.split(' ')[0];
    const match = await this.waitFor(new RegExp(`\\[RTPLAB\\] ((?:${word}|error) .*)`), timeout, `rtplab ${line}`);
    if (match[1].startsWith('error')) throw new Error(match[1]);
    return match[1];
  }

  async stop() {
    if (!this.process || this.process.exitCode !== null) return;
    this.command('stop');
    const timeout = sleep(90_000).then(() => 'timeout');
    if ((await Promise.race([this.exited, timeout])) === 'timeout') this.process.kill('SIGKILL');
  }
}

/** Plain text of a chat component: JSON text, a JSON string, or network NBT. */
export function plain(component) {
  if (component == null) return '';
  if (typeof component === 'number' || typeof component === 'boolean') return String(component);
  if (typeof component === 'string') {
    if (!/^[{\[]/.test(component.trim())) return component;
    try { return plain(JSON.parse(component)); } catch { return component; }
  }
  if (Array.isArray(component)) return component.map(plain).join('');
  if (typeof component.type === 'string' && 'value' in component) return plain(nbt.simplify(component));
  const text = component.text ?? component[''] ?? '';
  return (typeof text === 'string' ? text : plain(text)) + (Array.isArray(component.extra) ? component.extra.map(plain).join('') : '');
}

/**
 * A mineflayer bot with its chat, action bar and boss bars recorded. Bots stand still unless {@code moves} is set:
 * through ViaBackwards, newer servers can reject an older client's movement packets.
 */
export async function joinBot(name, version, chatFile, { moves = false } = {}, port = PORT) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, version, auth: 'offline', checkTimeoutInterval: 120_000 });
  bot.chatLog = [];
  bot.actionBars = [];
  bot.barTitles = [];
  const record = (kind, text) => chatFile && fs.appendFileSync(chatFile, `${kind}\t${text}\n`);
  bot.on('messagestr', (message, position) => {
    if (position === 'game_info') bot.actionBars.push(message);
    else bot.chatLog.push(message);
    record(position, message);
  });
  // Paper sends action bars as their own packet, which mineflayer does not turn into an event.
  bot._client.on('packet', (data, meta) => {
    if (/action_bar/.test(meta.name)) {
      const text = plain(data.text);
      bot.actionBars.push(text);
      record('action_bar', text);
    }
  });
  const onBar = (bar) => {
    const text = plain(bar._title?.json ?? bar.title?.json ?? '');
    bot.barTitles.push(text);
    record('boss_bar', text);
  };
  bot.on('bossBarCreated', onBar);
  bot.on('bossBarUpdated', onBar);
  if (!moves) {
    bot.physicsEnabled = false;
    bot.once('login', () => { bot.physicsEnabled = false; });
    const write = bot._client.write.bind(bot._client);
    bot._client.write = (packet, params) => (/^(position|position_look|look|flying)$/.test(packet) ? undefined : write(packet, params));
  }
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${name} did not spawn`)), 120_000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(); });
    bot.once('kicked', (reason) => { clearTimeout(timer); reject(new Error(`${name} kicked: ${JSON.stringify(reason)}`)); });
    bot.once('error', (error) => { clearTimeout(timer); reject(error); });
  });
  await sleep(1500);
  return bot;
}

/** The DonutRTP 1.5.x config a real server would have, with values the scenario relies on. */
function legacyConfig() {
  return `warmup-seconds: 2
cooldown-seconds: 30
max-attempts: 60
instant-teleport: false
update-check:
  enabled: false
  notify-ops-on-join: true
worlds:
  overworld:
    world-name: world
    radius: 1500
    min-y: 50
  nether:
    world-name: world_nether
    radius: 800
    min-y: 32
  end:
    world-name: world_the_end
    radius: 2500
    min-y: 30
gui:
  fill-empty-slots: true
  items:
    overworld:
      slot: 11
      name: "&aOverworld RTP"
      lore: ["&7Click to randomly teleport"]
      material: GRASS_BLOCK
    nether:
      slot: 13
      name: "&cNether RTP"
      lore: ["&7Click to randomly teleport"]
      material: NETHERRACK
    end:
      slot: 15
      name: "&dEnd RTP"
      lore: ["&7Click to randomly teleport"]
      material: END_STONE
actionbar-cooldown:
  enabled: true
  sound: BLOCK_NOTE_BLOCK_HAT
  volume: 1.0
  pitch: 1.0
teleport-sound:
  enabled: true
  sound: ENTITY_ENDERMAN_TELEPORT
  volume: 1.0
  pitch: 1.0
rtp-zone:
  enabled: true
  destination-world-type: OVERWORLD
  worldguard:
    enabled: true
    region: "lab_exit"
    world: "world"
  trigger:
    mode: "ENTER"
  cooldown:
    seconds: 60
  countdown:
    enabled: true
    seconds: 2
    cancel-on-move: false
rtp-zones:
  enabled: true
  zones:
    lab_pad:
      enabled: true
      world: world
      world-type: OVERWORLD
      x: 100.5
      y: 200.0
      z: 100.5
      half-size-x: 1
      half-size-y: 2
      half-size-z: 1
      countdown-seconds: 2
`;
}

const LEGACY_MESSAGES = `prefix: "&6&lRTP &8» "
countdown: "&7Teleporting in &e%seconds% &7seconds..."
cooldown: "&cYou must wait &e%time% seconds &cbefore using RTP again."
`;

function prepare(target, jars) {
  const dir = path.join(lab, 'servers', `${target.project}-${target.version}`);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins', 'DonutRTP'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'server.properties'), [
    // 26.x defaults white-list to true.
    'online-mode=false', 'white-list=false', 'enforce-secure-profile=false', `server-port=${PORT}`, 'level-seed=donutrtp-lab',
    'spawn-protection=0', 'view-distance=3', 'simulation-distance=3', 'allow-flight=true', 'motd=DonutRTP smoke',
    'difficulty=peaceful', 'spawn-monsters=false',
  ].join('\n') + '\n');
  for (const jar of jars) fs.copyFileSync(jar, path.join(dir, 'plugins', path.basename(jar)));
  // A 1.x install: the plugin must upgrade these files on its first 2.0 start.
  fs.writeFileSync(path.join(dir, 'plugins', 'DonutRTP', 'config.yml'), legacyConfig());
  fs.writeFileSync(path.join(dir, 'plugins', 'DonutRTP', 'messages.yml'), LEGACY_MESSAGES);
  // GriefPrevention kicks a fourth account from one address; every bot connects from 127.0.0.1.
  fs.mkdirSync(path.join(dir, 'plugins', 'GriefPreventionData'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'plugins', 'GriefPreventionData', 'config.yml'), 'GriefPrevention:\n  MaxPlayersPerIpAddress: 1000\n');
  return dir;
}

/** Plugin errors in the console: severe/error lines from the plugin, stack frames in its packages, linkage errors. */
export function pluginErrors(lines) {
  const problems = [];
  lines.forEach((line, index) => {
    const ours = /\[DonutRTP\]/.test(line) && /(ERROR|SEVERE)/.test(line);
    const frame = /at io\.nightbeam\.donutrtp\./.test(line);
    const load = /Could not load '.*DonutRTP|Error occurred while enabling DonutRTP|NoSuchMethodError|NoClassDefFoundError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError/.test(line);
    if (ours || load || (frame && !problems.includes(lines[index - 1]))) problems.push(line);
  });
  return problems;
}

async function runTarget(target) {
  const name = `${target.project}-${target.version}`;
  const started = Date.now();
  const java = await jdk(target.java);
  const jars = [pluginJar(), path.join(here, 'fixture', 'build', 'libs', 'RtpLabFixture.jar'), await economyJar(target.economy),
    cached('plugins', 'PlaceholderAPI.jar') || await modrinth('placeholderapi')];
  if (target.via) {
    jars.push(cached('plugins', 'ViaVersion.jar') || await modrinth('viaversion'));
    jars.push(cached('plugins', 'ViaBackwards.jar') || await modrinth('viabackwards'));
  }
  const game = target.version;
  const loader = target.project;
  const worldEdit = await modrinth('worldedit', loader, game).catch(() => null);
  const worldGuard = worldEdit && await modrinth('worldguard', loader, game).catch(() => null);
  const griefPrevention = loader === 'paper' ? await modrinth('griefprevention', 'paper', game).catch(() => null) : null;
  if (worldEdit && worldGuard) jars.push(worldEdit, worldGuard);
  if (griefPrevention) jars.push(griefPrevention);
  // What DonutRTP actually hooked into decides which steps run.
  const hooks = { worldGuard: false, griefPrevention: false };
  const dir = prepare(target, jars);
  const server = new Server(dir, java, await serverJar(target.project, target.version));
  const result = { target: name, java: target.java, bot: target.bot, economy: target.economy, hooks, steps: [], errors: [] };
  server.start();
  const bots = [];
  try {
    await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'server start');
    await server.waitFor(/\[RTPLAB\] api listening/, 30_000, 'fixture API listener');
    hooks.worldGuard = server.seen(/Hooked into WorldGuard/);
    hooks.griefPrevention = server.seen(/Hooked into GriefPrevention/);
    await scenario({
      server, target, hooks,
      joinBot: async (botName, options) => {
        const bot = await joinBot(botName, target.bot, path.join(dir, `chat-${botName}.log`), options);
        bots.push(bot);
        return bot;
      },
      step: (label, ok, detail = '') => {
        result.steps.push({ label, ok, detail });
        console.log(`  ${ok ? 'PASS' : 'FAIL'} ${label}${detail ? ` (${detail})` : ''}`);
      },
    });
  } catch (error) {
    result.steps.push({ label: 'scenario', ok: false, detail: error.message });
    console.log(`  FAIL scenario (${error.message})`);
  } finally {
    for (const bot of bots) {
      try { bot.quit(); } catch { /* already gone */ }
    }
    await server.stop();
  }
  result.errors = pluginErrors(server.lines);
  result.ok = result.steps.length > 0 && result.steps.every((s) => s.ok) && result.errors.length === 0;
  result.seconds = Math.round((Date.now() - started) / 1000);
  return result;
}

async function main() {
  const wanted = process.argv.slice(2);
  const targets = MATRIX.filter((t) => wanted.length === 0 || wanted.includes(`${t.project}-${t.version}`));
  const results = [];
  for (const target of targets) {
    console.log(`\n== ${target.project} ${target.version} (Java ${target.java}, bot ${target.bot}${target.via ? ' via ViaBackwards' : ''}, ${target.economy})`);
    const result = await runTarget(target);
    for (const error of result.errors.slice(0, 20)) console.log(`  ERROR ${error}`);
    console.log(`  ${result.ok ? 'PASS' : 'FAIL'} in ${result.seconds}s (WorldGuard: ${result.hooks.worldGuard}, GriefPrevention: ${result.hooks.griefPrevention})`);
    results.push(result);
  }
  fs.mkdirSync(path.join(lab, 'reports'), { recursive: true });
  const report = path.join(lab, 'reports', `smoke-${new Date().toISOString().replace(/[:.]/g, '-')}.json`);
  fs.writeFileSync(report, JSON.stringify(results, null, 2));
  console.log('\n| Server | Java | Steps | Plugin errors | WorldGuard | GriefPrevention | Result |\n| --- | --- | --- | --- | --- | --- | --- |');
  for (const r of results) {
    console.log(`| ${r.target} | ${r.java} | ${r.steps.filter((s) => s.ok).length}/${r.steps.length} | ${r.errors.length} | ${r.hooks.worldGuard ? 'yes' : '-'} | ${r.hooks.griefPrevention ? 'yes' : '-'} | ${r.ok ? 'PASS' : 'FAIL'} |`);
  }
  console.log(`\nReport: ${report}`);
  process.exit(results.every((r) => r.ok) ? 0 : 1);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
