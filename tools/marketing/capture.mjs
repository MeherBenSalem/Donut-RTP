// Captures the listing screenshots and GIFs from a real game: a Paper server running DonutRTP and the unmodified
// vanilla Minecraft client (software rendering under Xvfb), driven through console commands and XTEST mouse and
// keyboard input. A mineflayer bot is the second player in the group zone.
//
// Needs: Xvfb, ImageMagick (import), ffmpeg, python3 with python-xlib, portablemc, and the smoke-test setup
// (tools/smoke, its fixture built). Run from the repository root after ./gradlew build:
//   RTPLAB_PORT=25600 node tools/marketing/capture.mjs
import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Server, joinBot, jdk, serverJar, pluginJar, economyJar, modrinth } from '../smoke/run.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const lab = process.env.RTPLAB_HOME || path.join(os.homedir(), '.cache', 'donutrtp-smoke');
const PORT = Number(process.env.RTPLAB_PORT || 25600);
const VERSION = process.env.CAPTURE_VERSION || '1.21.11';
const PLAYER = 'Moonbeam';
const FRIEND = 'Kiri';
const DISPLAY = ':99';
const W = 1920;
const H = 1080;
const SCALE = 3;
const shots = path.join(repo, 'marketing', 'images', 'screenshots');
const gifs = path.join(repo, 'marketing', 'images', 'gifs');
const consoleDir = path.join(repo, 'marketing', 'source', 'console');
const python = process.env.CAPTURE_PYTHON || 'python3';
const portablemc = process.env.PORTABLEMC || 'portablemc';
// The DonutOrders capture already downloaded this client; reuse it when it is there.
const sharedClient = path.join(os.homedir(), '.cache', 'donutorders-smoke', 'client');
const clientDir = process.env.CAPTURE_CLIENT_DIR || (fs.existsSync(sharedClient) ? sharedClient : path.join(lab, 'client'));

// Noon, clear sky, no command echo in chat. 1.21.11 renamed the game rules, so both spellings are sent.
const WORLD_SETUP = ['time set 6000', 'weather clear',
  'gamerule advance_time false', 'gamerule advance_weather false', 'gamerule show_advancement_messages false', 'gamerule send_command_feedback false',
  'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule announceAdvancements false', 'gamerule sendCommandFeedback false'];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const x = (...args) => execFileSync(python, [path.join(here, 'xinput.py'), ...args.map(String)], { env: { ...process.env, DISPLAY } });

/** Pixel centre of a slot in a chest menu with the given number of rows (GUI scale 3). */
function slot(index, rows) {
  const left = Math.floor((W / SCALE - 176) / 2);
  const top = Math.floor((H / SCALE - (114 + rows * 18)) / 2);
  return [(left + 8 + (index % 9) * 18 + 8) * SCALE, (top + 18 + Math.floor(index / 9) * 18 + 8) * SCALE];
}

function screenshot(name, quality = 92) {
  fs.mkdirSync(shots, { recursive: true });
  const file = path.join(shots, `${name}.jpg`);
  execFileSync('import', ['-display', DISPLAY, '-window', 'root', '-quality', String(quality), file]);
  console.log(`  captured ${path.relative(repo, file)}`);
  return file;
}

async function hover(index, rows = 3, settle = 800) {
  const [px, py] = slot(index, rows);
  x('move', px - 9, py - 9);
  await sleep(120);
  x('move', px, py);
  await sleep(settle);
}

async function clickSlot(index, rows = 3, settle = 600) {
  const [px, py] = slot(index, rows);
  x('click', px, py, 1);
  await sleep(settle);
}

/** Records the screen for `seconds` (x11grab); returns a promise for the finished MP4. */
function record(name, seconds) {
  fs.mkdirSync(path.join(lab, 'capture'), { recursive: true });
  const raw = path.join(lab, 'capture', `${name}.mp4`);
  const proc = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'x11grab', '-video_size', `${W}x${H}`, '-framerate', '20', '-i', `${DISPLAY}.0`,
    '-t', String(seconds), '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '16', raw], { stdio: 'ignore' });
  return new Promise((resolve) => proc.on('exit', () => resolve(raw)));
}

/** A 640x360 GIF from a recording, cropped to the centre 1280x720 of the screen (or the whole screen). */
function gif(raw, name, { crop = true, start = 0, length = null } = {}) {
  fs.mkdirSync(gifs, { recursive: true });
  const file = path.join(gifs, `${name}.gif`);
  const view = crop ? 'crop=1280:720:320:180,' : '';
  const cut = [...(start ? ['-ss', String(start)] : []), ...(length ? ['-t', String(length)] : [])];
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...cut, '-i', raw, '-vf',
    `${view}fps=12,scale=640:360:flags=lanczos,split[a][b];[a]palettegen=max_colors=160:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle`,
    file]);
  console.log(`  recorded ${path.relative(repo, file)}`);
}

function writeClientOptions() {
  fs.mkdirSync(clientDir, { recursive: true });
  fs.writeFileSync(path.join(clientDir, 'options.txt'), [
    'version:4671', 'guiScale:3', 'renderDistance:10', 'simulationDistance:5', 'maxFps:30', 'graphicsMode:1',
    'pauseOnLostFocus:false', 'narrator:0', 'tutorialStep:none', 'skipMultiplayerWarning:true', 'joinedFirstServer:true',
    'onboardAccessibility:false', 'lang:en_us', 'fov:0.0', 'bobView:false', 'chatScale:1.0', 'chatWidth:1.0',
    'soundCategory_master:0.0', 'darkMojangStudiosBackground:true', 'hideServerAddress:true', 'renderClouds:"fancy"',
    'entityShadows:true', 'particles:0', 'mipmapLevels:4', 'ao:true', 'menuBackgroundBlurriness:3',
  ].join('\n') + '\n');
}

/** The shipped config.yml with the settings the shots show: a title countdown, a paid Nether and a locked End. */
function captureConfig() {
  let config = fs.readFileSync(path.join(repo, 'src', 'main', 'resources', 'config.yml'), 'utf8');
  config = config.replace(/(warmup:\n(?:.*\n)*?  seconds: )5/, '$13').replace(/(\n  title: )false/, '$1true')
    .replace(/(update-check:\n(?:.*\n)*?  enabled: )true/, '$1false');
  const nether = config.indexOf('\n  nether:');
  const end = config.indexOf('\n  end:');
  config = config.slice(0, nether) + config.slice(nether, end).replace('cost: 0', 'cost: 250') + config.slice(end);
  const endStart = config.indexOf('\n  end:');
  config = config.slice(0, endStart) + config.slice(endStart).replace('permission: ""', 'permission: "rtp.end"');
  return config;
}

async function prepareServer(java) {
  const dir = path.join(lab, 'capture', 'server');
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins', 'DonutRTP'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'server.properties'), [
    'online-mode=false', 'white-list=false', 'enforce-secure-profile=false', `server-port=${PORT}`, 'level-seed=8675309',
    'difficulty=peaceful', 'spawn-monsters=false', 'view-distance=10', 'simulation-distance=5', 'spawn-protection=0',
    'motd=DonutRTP', 'gamemode=survival', 'allow-flight=true',
  ].join('\n') + '\n');
  const jars = [pluginJar(), path.join(repo, 'tools', 'smoke', 'fixture', 'build', 'libs', 'RtpLabFixture.jar'),
    await economyJar('Vault'), await modrinth('placeholderapi')];
  for (const jar of jars) fs.copyFileSync(jar, path.join(dir, 'plugins', path.basename(jar)));
  fs.writeFileSync(path.join(dir, 'plugins', 'DonutRTP', 'config.yml'), captureConfig());
  return { dir, server: new Server(dir, java, await serverJar('paper', VERSION)) };
}

/** A server started with DonutRTP 1.x files: its console shows the upgrade (for the upgrade card). */
async function upgradeConsole(java) {
  const dir = path.join(lab, 'capture', 'upgrade');
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins', 'DonutRTP'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'server.properties'), ['online-mode=false', `server-port=${PORT}`, 'level-type=minecraft\\:flat',
    'generate-structures=false', 'view-distance=3'].join('\n') + '\n');
  fs.copyFileSync(pluginJar(), path.join(dir, 'plugins', path.basename(pluginJar())));
  // A 1.5.2 config as a real server has it (trimmed): a WorldGuard spawn zone and a cuboid pad.
  fs.writeFileSync(path.join(dir, 'plugins', 'DonutRTP', 'config.yml'), [
    'warmup-seconds: 5', 'cooldown-seconds: 300', 'max-attempts: 60', 'update-check:', '  enabled: false',
    'worlds:', '  overworld:', '    world-name: world', '    radius: 8000', '    min-y: 60',
    '  nether:', '    world-name: world_nether', '    radius: 4000', '    min-y: 40',
    '  end:', '    world-name: world_the_end', '    radius: 3000', '    min-y: 50',
    'rtp-zone:', '  enabled: true', '  destination-world-type: OVERWORLD', '  worldguard:', '    enabled: true', '    region: "spawn_rtp"',
    '    world: "world"', '  cooldown:', '    seconds: 120', '  countdown:', '    enabled: true', '    seconds: 5',
    'rtp-zones:', '  enabled: true', '  zones:', '    nether_pad:', '      enabled: true', '      world: world', '      world-type: NETHER',
    '      x: 20.5', '      y: 64', '      z: -8.5', '      half-size-x: 2', '      half-size-y: 2', '      half-size-z: 2', '      countdown-seconds: 10',
  ].join('\n') + '\n');
  const server = new Server(dir, java, await serverJar('paper', VERSION));
  server.start();
  await server.waitFor(/DonutRTP \d+\.\d+\.\d+ enabled on/, 300_000, 'upgrade start');
  await sleep(1000);
  const lines = server.lines.filter((line) => /\[DonutRTP\]/.test(line) && !/Loading server plugin|Enabling DonutRTP/.test(line))
    .map((line) => line.replace(/^\[[^\]]*(INFO|WARN)\]:? ?/, ''));
  await server.stop();
  fs.mkdirSync(consoleDir, { recursive: true });
  fs.writeFileSync(path.join(consoleDir, 'upgrade.txt'), lines.join('\n') + '\n');
  console.log(`  upgrade console: ${lines.length} lines`);
}

async function main() {
  const java = await jdk(21);
  await upgradeConsole(java);
  if (process.argv.includes('--upgrade-only')) return;
  const { server } = await prepareServer(java);
  server.start();
  await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'server start');
  for (const command of WORLD_SETUP) server.command(command);

  const xvfb = spawn('Xvfb', [DISPLAY, '-screen', '0', `${W}x${H}x24`, '-nolisten', 'tcp'], { stdio: 'ignore' });
  await sleep(1500);
  writeClientOptions();
  const client = spawn(portablemc, ['--main-dir', clientDir, 'start', `mojang:${VERSION}`, '-u', PLAYER, '--join-server', '127.0.0.1',
    '--join-server-port', String(PORT), '--resolution', `${W}x${H}`, '--jvm', java, '--jvm-arg=-Xmx3G'],
  { env: { ...process.env, DISPLAY, LIBGL_ALWAYS_SOFTWARE: '1', __GL_SYNC_TO_VBLANK: '0' }, stdio: ['ignore', 'pipe', 'pipe'] });
  fs.mkdirSync(path.join(lab, 'capture'), { recursive: true });
  const clientLog = fs.createWriteStream(path.join(lab, 'capture', 'client.log'));
  client.stdout.pipe(clientLog);
  client.stderr.pipe(clientLog);
  try {
    await server.waitFor(new RegExp(`${PLAYER} joined the game`), 600_000, 'client join');
    console.log('client joined');
    await scenes(server);
  } finally {
    client.kill('SIGTERM');
    await sleep(2000);
    await server.stop();
    xvfb.kill('SIGTERM');
  }
}

const feet = async (server, who) => {
  const line = await server.lab(`pos ${who}`);
  const m = line.match(/^pos \S+ (\S+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)/);
  return { world: m[1], x: Math.floor(Number(m[2])), y: Math.floor(Number(m[3])), z: Math.floor(Number(m[4])) };
};

async function scenes(server) {
  await server.lab(`money ${PLAYER} 5000`);
  server.command(`recipe give ${PLAYER} *`);

  // An RTP pad in a cherry grove: the zone scenes and the backdrop of every card.
  server.mark();
  server.command('locate biome minecraft:cherry_grove');
  const located = await server.waitFor(/cherry_grove is at \[(-?\d+), [^,]+, (-?\d+)\]/, 120_000, 'locate');
  server.command(`spreadplayers ${located[1]} ${located[2]} 0 1 false ${PLAYER}`);
  await sleep(2500);
  const pad = await feet(server, PLAYER);
  const { x: px, y: py, z: pz } = pad;
  for (const command of [
    `fill ${px - 4} ${py} ${pz - 4} ${px + 4} ${py + 5} ${pz + 4} air`,
    `fill ${px - 3} ${py - 1} ${pz - 3} ${px + 3} ${py - 1} ${pz + 3} purpur_block`,
    `fill ${px - 2} ${py - 1} ${pz - 2} ${px + 2} ${py - 1} ${pz + 2} amethyst_block`,
    `setblock ${px} ${py - 1} ${pz} crying_obsidian`,
    `setblock ${px - 3} ${py} ${pz - 3} end_rod`, `setblock ${px + 3} ${py} ${pz - 3} end_rod`,
    `setblock ${px - 3} ${py} ${pz + 3} end_rod`, `setblock ${px + 3} ${py} ${pz + 3} end_rod`,
  ]) server.command(command);
  await sleep(1000);

  // The zone, made the way an admin would: two corners and zone create.
  server.command(`op ${PLAYER}`);
  await server.lab(`tp ${PLAYER} world ${px - 2 + 0.5} ${py} ${pz - 2 + 0.5}`);
  await server.lab(`sudo ${PLAYER} rtpadmin pos1`);
  await server.lab(`tp ${PLAYER} world ${px + 2 + 0.5} ${py + 1} ${pz + 2 + 0.5}`);
  await server.lab(`sudo ${PLAYER} rtpadmin pos2`);
  await server.lab(`sudo ${PLAYER} rtpadmin zone create spawn overworld`);
  for (const setting of ['countdown 10', 'min-players 2', 'spread 3']) await server.lab(`sudo ${PLAYER} rtpadmin zone set spawn ${setting}`);
  server.command(`deop ${PLAYER}`);
  // A view over the pad from the south-east, a little above it.
  await server.lab(`tp ${PLAYER} world ${px + 6.5} ${py + 3} ${pz + 6.5}`);
  server.command(`tp ${PLAYER} ${px + 6.5} ${py + 3} ${pz + 6.5} 135 22`);
  await sleep(55_000); // terrain loads slowly with software rendering
  x('key', 'F1');
  await sleep(1500);
  screenshot('scenery');
  x('key', 'F1');
  await sleep(10_500); // the zone and setup messages fade from chat

  // The menu.
  const opening = record('menu', 16);
  await sleep(300);
  await server.lab(`sudo ${PLAYER} rtp`);
  await sleep(2500);
  await hover(11);
  screenshot('menu-overworld');
  await hover(13, 3, 1600);
  screenshot('menu-nether');
  await hover(15, 3, 1600);
  screenshot('menu-end');
  await hover(11, 3, 1200);
  // The warmup: title, action bar and chat, then the teleport.
  await clickSlot(11, 3, 900);
  screenshot('warmup');
  await sleep(1200);
  screenshot('warmup-2');
  await sleep(2600);
  screenshot('landed-chat');
  gif(await opening, 'menu');
  await sleep(45_000);
  x('key', 'F1');
  await sleep(1200);
  screenshot('overworld-view');
  x('key', 'F1');
  await sleep(800);

  // On cooldown: the menu says when, and so does /rtp.
  await server.lab(`sudo ${PLAYER} rtp`);
  await sleep(2500);
  await hover(11, 3, 1200);
  screenshot('menu-cooldown');
  x('key', 'Escape');
  await sleep(800);
  await server.lab(`sudo ${PLAYER} rtp overworld`);
  await sleep(1200);
  screenshot('cooldown-chat');

  // The Nether and the End, sent by the console (no warmup or cooldown).
  for (const destination of ['nether', 'end']) {
    server.command(`rtp player ${PLAYER} ${destination}`);
    await sleep(50_000);
    x('key', 'F1');
    await sleep(1200);
    screenshot(`${destination}-view`);
    x('key', 'F1');
    await sleep(800);
  }

  // The group zone: Moonbeam waits on the pad, Kiri steps on, and both land together.
  await server.lab(`tp ${PLAYER} world ${px - 1 + 0.5} ${py} ${pz + 0.5}`);
  server.command(`tp ${PLAYER} ${px - 1 + 0.5} ${py} ${pz + 0.5} -90 8`);
  await sleep(45_000);
  screenshot('zone-waiting');
  const kiri = await joinBot(FRIEND, VERSION, null, {}, PORT);
  try {
    await sleep(1500);
    const countdown = record('zone', 18);
    await sleep(500);
    await server.lab(`tp ${FRIEND} world ${px + 1 + 0.5} ${py} ${pz + 0.5}`);
    server.command(`tp ${FRIEND} ${px + 1 + 0.5} ${py} ${pz + 0.5} 90 0`);
    await sleep(3200);
    screenshot('zone-countdown');
    await sleep(3000);
    screenshot('zone-countdown-2');
    await server.waitFor(new RegExp(`event teleport ${PLAYER} overworld ZONE`), 30_000, 'group teleport').catch(() => null);
    await sleep(2500);
    screenshot('zone-landed-chat');
    gif(await countdown, 'zone');
    server.command(`execute as ${PLAYER} at @s facing entity ${FRIEND} eyes run tp @s ~ ~ ~ ~ ~`);
    await sleep(45_000);
    screenshot('zone-landed');
  } finally {
    kiri.quit();
  }

  // Staff: zone info in chat, and the console report.
  server.command(`op ${PLAYER}`);
  await sleep(10_500);
  await server.lab(`sudo ${PLAYER} rtpadmin zone info spawn`);
  await sleep(1500);
  screenshot('zone-info');
  server.mark();
  const from = server.lines.length;
  server.command('rtpadmin info');
  await sleep(2500);
  const report = server.lines.slice(from).map((line) => line.replace(/^\[[^\]]*INFO\]:? ?/, '')).filter((line) => line.trim());
  fs.mkdirSync(consoleDir, { recursive: true });
  fs.writeFileSync(path.join(consoleDir, 'admin-info.txt'), report.join('\n') + '\n');
  console.log(`  console: ${report.length} lines`);
}

/** Servers started by this run, killed if it fails so their ports are free for the next one. */
const started = [];
const originalStart = Server.prototype.start;
Server.prototype.start = function start() {
  started.push(this);
  return originalStart.call(this);
};

main().catch((error) => {
  console.error(error);
  for (const server of started) if (server.process && server.process.exitCode === null) server.process.kill('SIGKILL');
  process.exit(1);
});
