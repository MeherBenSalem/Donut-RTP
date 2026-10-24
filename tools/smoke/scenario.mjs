// The DonutRTP smoke scenario, run once per server in the matrix. See README.md for what each step proves.
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Waits for a chat line on the bot after the last one matched. */
async function chat(bot, pattern, timeout = 20_000) {
  const deadline = Date.now() + timeout;
  bot.seenChat = bot.seenChat || 0;
  while (Date.now() < deadline) {
    for (let i = bot.seenChat; i < bot.chatLog.length; i++) {
      const match = bot.chatLog[i].match(pattern);
      if (match) {
        bot.seenChat = i + 1;
        return match;
      }
    }
    await sleep(100);
  }
  throw new Error(`${bot.username} never saw ${pattern} (last: ${JSON.stringify(bot.chatLog.slice(-3))})`);
}

/** Waits for a line in a list the bot fills (action bars, boss bars). */
async function seen(list, pattern, timeout = 15_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const hit = list.find((line) => pattern.test(line));
    if (hit) return hit;
    await sleep(100);
  }
  throw new Error(`never saw ${pattern} (last: ${JSON.stringify(list.slice(-3))})`);
}

function parsePos(line) {
  const m = line.match(/^pos (\S+) (\S+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+) floor=(\w+)\/(\w+) feet=(\w+)\/(\w+)\/(\w+) head=(\w+)\/(\w+)\/(\w+) biome=(\S+)/);
  if (!m) throw new Error(`bad pos line: ${line}`);
  return {
    name: m[1], world: m[2], x: Number(m[3]), y: Number(m[4]), z: Number(m[5]),
    floor: m[6], floorSolid: m[7] === 'true', feet: m[8], feetClear: m[9] === 'true' && m[10] === 'false',
    head: m[11], headClear: m[12] === 'true' && m[13] === 'false', biome: m[14],
  };
}

const safe = (p) => p.floorSolid && p.feetClear && p.headClear && !/LAVA|WATER|MAGMA|CACTUS|FIRE/.test(p.floor + p.feet + p.head);
const describe = (p) => `${p.world} ${Math.round(p.x)} ${Math.round(p.y)} ${Math.round(p.z)} on ${p.floor}, ${p.biome}`;

export async function scenario({ server, target, hooks, joinBot, step }) {
  const via = Boolean(target.via);
  const check = async (label, body) => {
    try {
      const detail = await body();
      step(label, true, typeof detail === 'string' ? detail : '');
    } catch (error) {
      step(label, false, error.message);
    }
  };
  const teleported = (who, destination, source, timeout = 45_000) => server.waitFor(
    new RegExp(`\\[RTPLAB\\] event teleport ${who} ${destination} ${source} (\\S+) (-?\\d+) (-?\\d+) (-?\\d+)`), timeout,
    `${who} teleported to ${destination} by ${source}`);
  const pos = async (who) => parsePos(await server.lab(`pos ${who}`));
  const reload = async () => {
    server.mark();
    server.command('rtpadmin reload');
    await server.waitFor(/DonutRTP reloaded/, 20_000, 'reload');
  };

  // 1. Start-up and the 1.x upgrade.
  await check('plugin enables', async () => {
    const line = server.lines.find((l) => /DonutRTP 2\.\d+\.\d+ enabled on/.test(l));
    if (!line) throw new Error('no enable line');
    if (!(target.project === 'folia' ? /enabled on Folia/ : /enabled on Paper/).test(line)) throw new Error(line);
    return line.replace(/.*enabled on /, '');
  });
  await check('1.x config upgraded', async () => {
    if (!server.seen(/Upgraded your DonutRTP 1\.x config/)) throw new Error('no upgrade line');
    if (!/exists=true/.test(await server.lab('file config-1.x.yml'))) throw new Error('no config-1.x.yml backup');
    if (!/=2$/.test(await server.lab('yaml config.yml warmup.seconds'))) throw new Error('warmup not migrated');
    if (!/=1500$/.test(await server.lab('yaml config.yml destinations.overworld.max-radius'))) throw new Error('radius not migrated');
    if (!/=\[99, 198, 99\]$/.test(await server.lab('yaml zones.yml zones.lab_pad.area.min'))) throw new Error('cuboid zone not migrated');
    if (!/=lab_exit$/.test(await server.lab('yaml zones.yml zones.lab_exit.area.region'))) throw new Error('WorldGuard zone not migrated');
    return 'config, zones and messages';
  });
  if (hooks.worldGuard) await server.lab('region world lab_exit 200 190 200 202 210 202');

  // 2. The menu.
  const alice = await joinBot('Alice');
  let aliceSpot = null;
  await check('menu opens with the destinations', async () => {
    const opened = new Promise((resolve) => alice.once('windowOpen', resolve));
    alice.chat('/rtp');
    const window = await Promise.race([opened, sleep(15_000).then(() => { throw new Error('menu did not open'); })]);
    const title = JSON.stringify(window.title);
    if (!title.includes('ʀᴀɴᴅᴏᴍ')) throw new Error(`title ${title}`);
    const names = [11, 13, 15].map((slot) => window.slots[slot]?.name);
    if (names.join() !== 'grass_block,netherrack,end_stone') throw new Error(`items ${names}`);
    if (window.slots[0]?.name !== 'gray_stained_glass_pane') throw new Error(`filler ${window.slots[0]?.name}`);
    return names.join(', ');
  });
  await check('menu click teleports after the warmup', async () => {
    server.mark();
    await alice.clickWindow(11, 0, 0);
    await chat(alice, /Teleporting in 2 seconds/);
    await teleported('Alice', 'overworld', 'MENU');
    await chat(alice, /Teleported to -?\d+, -?\d+, -?\d+ in Overworld RTP/);
    const p = await pos('Alice');
    const distance = Math.max(Math.abs(p.x), Math.abs(p.z));
    if (p.world !== 'world' || distance < 299 || distance > 1501) throw new Error(`out of range: ${describe(p)}`);
    if (!safe(p)) throw new Error(`unsafe: ${describe(p)}`);
    if (/ocean|river/.test(p.biome)) throw new Error(`denied biome: ${describe(p)}`);
    aliceSpot = p;
    return describe(p);
  });
  await check('cooldown blocks the next teleport', async () => {
    alice.chat('/rtp overworld');
    await chat(alice, /You can teleport again in \d+s/);
    server.mark();
    server.command('papi parse Alice CD=%donutrtp_cooldown%');
    const match = await server.waitFor(/CD=(\S+)/, 10_000, 'placeholder');
    if (!/^\d+s$/.test(match[1])) throw new Error(`placeholder said ${match[1]}`);
    return `placeholder ${match[1]}`;
  });

  // 3. The Nether, and damage cancelling the warmup.
  const bob = await joinBot('Bob');
  await check('damage cancels the warmup', async () => {
    // Players cannot be hurt for 3 seconds after joining.
    await sleep(3_500);
    bob.chat('/rtp nether');
    await chat(bob, /Teleporting in/);
    await server.lab('damage Bob 1');
    await chat(bob, /cancelled because you took damage/);
  });
  await check('Nether teleport lands under the roof', async () => {
    // Bob has a rank with a 5-second cooldown (cooldown.ranks.mvp), checked in the next step.
    await server.lab('set cooldown.ranks.mvp 5');
    await reload();
    await server.lab('perm Bob donutrtp.cooldown.mvp true');
    server.mark();
    bob.chat('/rtp nether');
    await teleported('Bob', 'nether', 'COMMAND');
    const p = await pos('Bob');
    if (p.world !== 'world_nether' || p.y < 33 || p.y > 126) throw new Error(`bad spot: ${describe(p)}`);
    if (!safe(p) || p.floor === 'BEDROCK') throw new Error(`unsafe: ${describe(p)}`);
    return describe(p);
  });
  await check('rank cooldown is shorter', async () => {
    bob.chat('/rtp nether');
    const match = await chat(bob, /You can teleport again in (\d+)s/);
    if (Number(match[1]) > 5) throw new Error(`rank cooldown ${match[1]}s, expected at most 5s`);
    return `${match[1]}s instead of 30s`;
  });

  // 4. The End, being moved during the warmup, /rtp player and landing protection.
  const cara = await joinBot('Cara');
  await check('being moved cancels the warmup', async () => {
    cara.chat('/rtp end');
    await chat(cara, /Teleporting in/);
    await server.lab('tp Cara world 5.5 150 5.5');
    await chat(cara, /cancelled because you moved/);
  });
  await check('/rtp player sends a player to the End', async () => {
    server.mark();
    server.command('rtp player Cara end');
    await teleported('Cara', 'end', 'ADMIN', 60_000);
    if (!server.seen(/Sending Cara to End RTP/)) throw new Error('no confirmation for the console');
    const p = await pos('Cara');
    if (p.world !== 'world_the_end' || Math.hypot(p.x, p.z) < 620) throw new Error(`bad spot: ${describe(p)}`);
    if (!safe(p)) throw new Error(`unsafe: ${describe(p)}`);
    return describe(p);
  });
  await check('landing protection, then normal damage', async () => {
    if (!/cancelled=true/.test(await server.lab('fall Cara'))) throw new Error('fall damage right after landing');
    await sleep(6_000);
    if (!/cancelled=false/.test(await server.lab('fall Cara'))) throw new Error('still protected after 6s');
  });

  // 5. Walking off the block cancels; jumping in place does not. Only where bots can move (no ViaBackwards).
  if (!via) {
    const walker = await joinBot('Walker', { moves: true });
    await check('walking cancels, jumping does not', async () => {
      // A flat floor near spawn, so walking always reaches the next block.
      await server.lab('platform world 40 120 40');
      await server.lab('tp Walker world 40.5 120 40.5');
      // Walk only once the client agrees it is on the platform and has the platform's chunk (mineflayer does not move a
      // bot over blocks it has not received); before that the server rejects its moves.
      const onPlatform = () => Math.abs(walker.entity.position.x - 40.5) < 1 && Math.abs(walker.entity.position.z - 40.5) < 1
        && Math.abs(walker.entity.position.y - 120) < 1.5 && walker.blockAt(walker.entity.position.offset(1, -1, 0))?.name === 'stone';
      const reached = Date.now();
      for (let i = 0; i < 100 && !onPlatform(); i++) await sleep(100);
      if (!onPlatform()) throw new Error(`the bot never reached the platform: ${walker.entity.position}`);
      const waited = Date.now() - reached;
      await sleep(1_500);
      walker.chat('/rtp overworld');
      await chat(walker, /Teleporting in/);
      const start = walker.entity.position.clone();
      walker.setControlState('forward', true);
      try {
        await chat(walker, /cancelled because you moved/, 1_800);
      } catch (error) {
        const there = await pos('Walker').catch(() => null);
        throw new Error(`${error.message}; the bot walked from ${start} to ${walker.entity.position}, the server has it at `
          + `${there ? `${there.x} ${there.y} ${there.z}` : 'unknown'}`);
      } finally {
        walker.setControlState('forward', false);
      }
      await sleep(1_500);
      server.mark();
      walker.chat('/rtp overworld');
      await chat(walker, /Teleporting in/);
      walker.setControlState('jump', true);
      await teleported('Walker', 'overworld', 'COMMAND');
      walker.setControlState('jump', false);
      return `on the platform after ${waited} ms`;
    });
    walker.quit();
  }

  // 6. A group zone made in game: two players land together.
  server.command('op Opal');
  const opal = await joinBot('Opal');
  await check('zone created with pos1, pos2 and zone create', async () => {
    await server.lab('tp Opal world 300.5 200 300.5');
    await sleep(500);
    opal.chat('/rtpadmin pos1');
    await chat(opal, /Corner 1 set to 300, 200, 300/);
    await server.lab('tp Opal world 304.5 202 304.5');
    await sleep(500);
    opal.chat('/rtpadmin pos2');
    await chat(opal, /Corner 2 set to 304, 202, 304/);
    opal.chat('/rtpadmin zone create pad overworld');
    await chat(opal, /Zone pad created/);
    for (const [key, value] of [['countdown', 3], ['min-players', 2], ['spread', 4]]) {
      opal.chat(`/rtpadmin zone set pad ${key} ${value}`);
      await chat(opal, new RegExp(`${key} set to ${value}`));
    }
    await server.lab('tp Opal world 0.5 150 0.5');
  });
  const dan = await joinBot('Dan');
  let groupSpot = null;
  const eve = await joinBot('Eve');
  await check('group zone waits for enough players', async () => {
    await server.lab('tp Dan world 302.5 200 302.5');
    await chat(dan, /entered an RTP zone/);
    await seen(dan.actionBars, /Waiting for 1 more player/);
    server.mark();
    server.command('papi parse Dan ZP=%donutrtp_zone_pad_players%');
    const match = await server.waitFor(/ZP=(\S+)/, 10_000, 'zone placeholder');
    if (match[1] !== '1') throw new Error(`players placeholder ${match[1]}`);
  });
  await check('group zone teleports everyone together', async () => {
    server.mark();
    await server.lab('tp Eve world 301.5 200 301.5');
    await chat(eve, /entered an RTP zone/);
    await seen(dan.barTitles, /Random teleport in \ds/, 10_000);
    const first = await server.waitFor(/\[RTPLAB\] event teleport (Dan|Eve) overworld ZONE/, 45_000, 'first group teleport');
    const second = await server.waitFor(/\[RTPLAB\] event teleport (Dan|Eve) overworld ZONE/, 15_000, 'second group teleport');
    if (first[1] === second[1]) throw new Error('the same player twice');
    await chat(dan, /Teleported with 2 player/);
    const a = await pos('Dan');
    const b = await pos('Eve');
    groupSpot = a;
    const apart = Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    if (a.world !== b.world || apart > 9) throw new Error(`${describe(a)} vs ${describe(b)}`);
    if (Math.floor(a.x) === Math.floor(b.x) && Math.floor(a.z) === Math.floor(b.z)) throw new Error(`both on one block: ${describe(a)}`);
    if (!safe(a) || !safe(b)) throw new Error(`unsafe: ${describe(a)} / ${describe(b)}`);
    return `${apart} blocks apart at ${describe(a)}`;
  });
  await check('zone cooldown', async () => {
    await server.lab('tp Dan world 302.5 200 302.5');
    await chat(dan, /use an RTP zone again in \d+s/);
  });

  // 7. Zones upgraded from 1.x: the cuboid pad and the WorldGuard region.
  const finn = await joinBot('Finn');
  await check('1.x cuboid zone teleports', async () => {
    server.mark();
    await server.lab('tp Finn world 100.5 200 100.5');
    await chat(finn, /entered an RTP zone/);
    await teleported('Finn', 'overworld', 'ZONE');
  });
  if (hooks.worldGuard) {
    await check('1.x WorldGuard region zone teleports', async () => {
      server.mark();
      await server.lab('tp Finn world 201.5 200 201.5');
      await chat(finn, /entered an RTP zone/);
      await teleported('Finn', 'overworld', 'ZONE');
    });
  }

  // 8. Never landing in WorldGuard regions or GriefPrevention claims.
  const hal = await joinBot('Hal');
  await server.lab('perm Hal donutrtp.cooldown.bypass true');
  await server.lab('perm Hal donutrtp.warmup.bypass true');
  const center = aliceSpot ? [Math.round(aliceSpot.x), Math.round(aliceSpot.z)] : [700, 700];
  const destination = async (id, [x, z], extra = {}) => {
    const values = { world: 'world', 'center.x': x, 'center.z': z, 'min-radius': 0, 'max-radius': 20, 'min-y': -64, 'max-y': 320, ...extra };
    for (const [key, value] of Object.entries(values)) await server.lab(`set destinations.${id}.${key} ${value}`);
  };
  if (hooks.worldGuard) {
    await check('WorldGuard regions are avoided', async () => {
      await server.lab(`region world guard ${center[0] - 40} -64 ${center[1] - 40} ${center[0] + 40} 320 ${center[1] + 40}`);
      await destination('guarded', center);
      await reload();
      hal.chat('/rtp guarded');
      await chat(hal, /No safe spot was found/, 45_000);
      await server.lab('set search.avoid-regions false');
      await reload();
      server.mark();
      hal.chat('/rtp guarded');
      await teleported('Hal', 'guarded', 'COMMAND');
      await server.lab('set search.avoid-regions true');
    });
  }
  if (hooks.griefPrevention) {
    await check('GriefPrevention claims are avoided', async () => {
      // Somewhere other than the WorldGuard test, on land: where the group zone sent Dan.
      const claimed = groupSpot ? [Math.round(groupSpot.x), Math.round(groupSpot.z)] : [center[0] + 200, center[1]];
      const result = await server.lab(`claim world ${claimed[0] - 40} ${claimed[1] - 40} ${claimed[0] + 40} ${claimed[1] + 40}`);
      if (!/created=true/.test(result)) throw new Error(result);
      await destination('claimed', claimed);
      await reload();
      hal.chat('/rtp claimed');
      await chat(hal, /No safe spot was found/, 45_000);
      await server.lab('set search.avoid-claims false');
      await reload();
      server.mark();
      hal.chat('/rtp claimed');
      await teleported('Hal', 'claimed', 'COMMAND');
      await server.lab('set search.avoid-claims true');
    });
  }

  // 9. Permissions and costs.
  const ivy = await joinBot('Ivy');
  await check('destination permission', async () => {
    await destination('vip', center, { 'max-radius': 64, permission: 'lab.vip' });
    await destination('paid', center, { 'max-radius': 64, cost: 50 });
    await reload();
    ivy.chat('/rtp vip');
    await chat(ivy, /You cannot use that destination/);
    await server.lab('perm Ivy lab.vip true');
    await server.lab('perm Ivy donutrtp.warmup.bypass true');
    server.mark();
    ivy.chat('/rtp vip');
    await teleported('Ivy', 'vip', 'COMMAND');
  });
  await check('destination cost through Vault', async () => {
    await server.lab('perm Ivy donutrtp.cooldown.bypass true');
    await server.lab('money Ivy 10');
    ivy.chat('/rtp paid');
    await chat(ivy, /You need \$50\.00 to teleport there/);
    await server.lab('money Ivy 120');
    server.mark();
    ivy.chat('/rtp paid');
    await teleported('Ivy', 'paid', 'COMMAND');
    await chat(ivy, /Paid \$50\.00/);
    const balance = await server.lab('balance Ivy');
    if (!/ 70(\.0+)?$/.test(balance)) throw new Error(balance);
  });

  // 10. Admin commands from the console.
  await check('admin info and zone list', async () => {
    server.mark();
    server.command('rtpadmin info');
    await server.waitFor(/DonutRTP 2\.\d+\.\d+ - (Paper|Folia)/, 10_000, 'info header');
    await server.waitFor(/zone pad group -> overworld/, 10_000, 'zone line');
    server.mark();
    server.command('rtpadmin zone list');
    await server.waitFor(/lab_pad individual -> overworld/, 10_000, 'zone list');
  });
}
