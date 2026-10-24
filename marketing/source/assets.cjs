// The listing artwork. Each entry returns { html, width, height, transparent?, file? } for render.cjs.
// Crops are pixel boxes [x, y, w, h] of the 1920x1080 captures in images/screenshots/ (GUI scale 3). Menus and their
// tooltips are measured by tools/marketing/crops.py into crops.json ("<name>-tip" is a tooltip alone); chat lines,
// titles and boss bars sit at fixed places on screen and are listed here.
const fs = require('fs');
const path = require('path');
const r = require('./render.cjs');

const HEADERS = {
  menu: 'The Menu', teleporting: 'Teleporting', zones: 'RTP Zones', safety: 'Safe Landings', performance: 'No Lag',
  destinations: 'Destinations', cooldowns: 'Cooldowns & Costs', integrations: 'Integrations', upgrade: 'Upgrading from 1.x',
  compatibility: 'Compatibility', commands: 'Commands & Permissions', faq: 'Questions', gallery: 'Gallery',
};

const MEASURED = path.join(__dirname, 'crops.json');
const CROP = Object.assign({
  // The title and action bar of the warmup, the boss bar and title of a group countdown, and chat lines.
  'warmup-scene': [320, 150, 1280, 840],
  'zone-scene': [240, 0, 1440, 900],
  'landed-chat-line': [0, 905, 1150, 70],
  'cooldown-chat-line': [0, 905, 1150, 70],
  'zone-landed-chat-line': [0, 870, 1150, 105],
  'zone-info-chat': [0, 640, 1150, 335],
  'overworld-view': [0, 0, 1920, 1080],
  'nether-view': [0, 0, 1920, 1080],
  'end-view': [0, 0, 1920, 1080],
  'zone-landed-view': [0, 0, 1920, 1080],
}, fs.existsSync(MEASURED) ? JSON.parse(fs.readFileSync(MEASURED, 'utf8')) : {});
const FILES = {
  'warmup-scene': 'warmup', 'zone-scene': 'zone-countdown', 'landed-chat-line': 'landed-chat', 'cooldown-chat-line': 'cooldown-chat',
  'zone-landed-chat-line': 'zone-landed-chat', 'zone-info-chat': 'zone-info', 'zone-landed-view': 'zone-landed',
};

/** Width and height of a crop scaled to fit maxW x maxH. */
const fitted = (name, maxW, maxH) => {
  const box = CROP[name];
  if (!box) throw new Error(`no crop for ${name}`);
  const width = Math.round(Math.min(maxW, maxH * box[2] / box[3]));
  return [width, Math.round(width * box[3] / box[2])];
};
const fileOf = (name) => (FILES[name] || name.replace(/-tip$/, '')) + '.jpg';

/** A framed crop of a capture, as wide as possible within maxW x maxH. */
const shotArt = (name, maxW, maxH, style = '') => r.framed(r.region(fileOf(name), ...CROP[name], fitted(name, maxW, maxH)[0]), style);

// The card art area is 850 x 780. main() sits top-left, inset() bottom-right; each label is pinned to its frame.
const ART_W = 850;
const ART_H = 780;
const main = (name, maxW, maxH, top, text, kind = '') =>
  `${shotArt(name, maxW, maxH, `position:absolute;left:0;top:${top}px`)}${text ? r.label(text, `left:24px;top:${top - 22}px`, kind) : ''}`;
const inset = (name, maxW, maxH, bottom, text, kind = 'gold') => {
  const [w, h] = fitted(name, maxW, maxH);
  const left = ART_W + 20 - w;
  const top = ART_H - bottom - h;
  const labelTop = h < 200 ? top - 50 : top - 22;
  return `${shotArt(name, maxW, maxH, `position:absolute;left:${left}px;top:${top}px`)}${text ? r.label(text, `left:${left + 24}px;top:${labelTop}px`, kind) : ''}`;
};

// Card art is built only when that card renders, so one missing capture does not stop the others.
const card = (key, options) => () => ({ width: 1600, height: 900, file: `card-${key}.png`, html: r.card(options()) });
const command = (text) => `<span style="color:var(--gold-hi)">${r.escapeHtml(text)}</span>`;
const comment = (text) => `<span style="color:var(--faint)">${r.escapeHtml(text)}</span>`;

const assets = {
  icon: () => ({ width: 512, height: 512, html: r.page(512, 512, `
    <div style="position:absolute;inset:0;background:radial-gradient(circle at 50% 42%,#2c2147 0%,#161026 58%,#0b0814 100%)"></div>
    ${r.sprinkles(26, 0.55)}
    <div style="position:absolute;left:46px;top:46px;width:420px;height:420px;border-radius:50%;
      background:radial-gradient(circle,rgba(167,123,255,.34) 0%,rgba(167,123,255,0) 66%)"></div>
    <div style="position:absolute;left:64px;top:56px;filter:drop-shadow(0 18px 26px rgba(0,0,0,.6))">${r.donutSvg(384)}</div>
    <div style="position:absolute;right:40px;bottom:40px;display:flex;align-items:center;gap:10px;padding:12px 24px 11px 20px;border-radius:999px;
      background:linear-gradient(180deg,#ffd76a,#f0a92e);box-shadow:0 10px 26px rgba(0,0,0,.55),inset 0 -3px 0 rgba(120,70,0,.35)">
      <span style="font-family:DoDisplay;font-weight:900;font-size:44px;line-height:1;color:#3b2203;letter-spacing:.04em">RTP</span></div>`) }),

  divider: () => ({ width: 1400, height: 40, transparent: true, html: r.divider() }),

  banner: () => ({ width: 1800, height: 600, html: r.page(1800, 600, `
    ${r.backdrop(0.5, 6)}
    ${r.sprinkles(60, 0.4)}
    <div style="position:absolute;left:880px;top:-40px;width:1120px;height:700px;perspective:1700px">
      <div style="transform:rotateY(-17deg) rotateX(4deg) translateZ(0);transform-origin:left center;margin-top:70px">
        ${shotArt('menu-overworld', 800, 470)}
      </div>
    </div>
    <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(14,10,20,.95) 0%,rgba(14,10,20,.86) 40%,rgba(14,10,20,.15) 62%,rgba(14,10,20,0) 100%)"></div>
    <div style="position:absolute;left:90px;top:0;bottom:0;width:820px;display:flex;flex-direction:column;justify-content:center;z-index:2">
      <div class="kicker"><span class="dot"></span>${r.RELEASE.version} · ${r.RELEASE.name}</div>
      <div style="display:flex;align-items:center;gap:26px;margin:22px 0 6px">
        <div style="width:118px;height:118px;filter:drop-shadow(0 12px 20px rgba(0,0,0,.6))">${r.donutSvg(118)}</div>
        <div class="display" style="font-size:104px;line-height:.92">Donut<br><span class="grad">RTP</span></div>
      </div>
      <div class="rule" style="width:72%;margin:26px 0 24px"></div>
      <div style="font-size:31px;line-height:1.35;color:var(--text)">Pick a world, or step on the pad with your friends.<br>Land somewhere safe, without the lag. <b class="hi">Free.</b></div>
      <div style="margin-top:30px;display:flex;align-items:center">
        <span class="pill"><i></i>Paper</span><span class="pill"><i></i>Folia</span><span class="pill"><i></i>Purpur</span><span class="pill"><i></i>Spigot</span>
        <span style="font-family:DoDisplay;font-weight:700;font-size:26px;color:var(--gold-hi);margin-left:12px">${r.RANGE}</span>
      </div>
    </div>`) }),

  menu: card('menu', () => ({
    kicker: 'The Menu', title: 'Pick where you land',
    points: [
      'One item per destination, as many as you like',
      'Your <b class="hi">cooldown</b>, the <b class="hi">cost</b> and a <b class="hi">lock</b> on each item',
      '<code class="inline">/rtp nether</code> skips the menu',
      'Any item, HeadDatabase or custom heads as icons',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('menu-overworld', 850, 450, 40, '/rtp')}
      ${inset('menu-nether-tip', 330, 260, 0, 'A paid destination')}
    </div>`,
  })),

  warmup: card('warmup', () => ({
    kicker: 'Teleporting', title: 'Stand still, then go',
    points: [
      'The countdown on the action bar, a title, or both',
      'Moving or taking damage cancels; <b class="hi">jumping never does</b>',
      '<b class="hi">No fall damage</b> right after landing',
      'Sounds, particles and console commands on landing',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('warmup-scene', 850, 520, 40, 'The warmup')}
      ${inset('landed-chat-line', 760, 70, 40, 'Landed')}
    </div>`,
  })),

  zones: card('zones', () => ({
    kicker: 'RTP Zones', title: 'Land together',
    points: [
      'Everyone on the pad when it ends <b class="hi">lands together</b>',
      'Waits for a minimum number of players',
      'Boss bar, title and action bar, each switchable',
      'Solo zones too, with a cooldown and permission per zone',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('zone-scene', 850, 520, 40, 'A group countdown')}
      ${inset('zone-landed-chat-line', 760, 100, 30, 'Two players, one landing')}
    </div>`,
  })),

  'zone-admin': card('zone-admin', () => ({
    kicker: 'RTP Zones', title: 'Zones made in game',
    points: [
      'Two corners and <code class="inline">zone create</code>; no WorldGuard needed',
      'Or use a <b class="hi">WorldGuard region</b>',
      'Change anything with <code class="inline">zone set</code>, no restart',
      'Timers for holograms through PlaceholderAPI',
    ],
    art: `<div style="position:relative;height:780px">
      <div style="position:absolute;left:0;top:10px">${r.codePanel('In game', [
        comment('# stand on one corner, then the other'),
        command('/rtpadmin pos1'),
        command('/rtpadmin pos2'),
        command('/rtpadmin zone create spawn overworld'),
        comment('# or from a WorldGuard region'),
        command('/rtpadmin zone create pad overworld region spawn_pad'),
        comment('# then tune it'),
        command('/rtpadmin zone set spawn min-players 2'),
        command('/rtpadmin zone set spawn countdown 10'),
      ].join('\n'), { width: 760, size: 19 })}</div>
      ${inset('zone-info-chat', 700, 300, 0, '/rtpadmin zone info')}
    </div>`,
  })),

  safety: card('safety', () => ({
    kicker: 'Safe Landings', title: 'Never into lava',
    points: [
      'Solid ground, open air: no water, lava, trees or the Nether roof',
      '<b class="hi">Never in a WorldGuard region or GriefPrevention claim</b>',
      'Always inside the world border',
      'Biome rules: oceans and rivers off by default',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('overworld-view', 850, 480, 40, 'A random landing')}
      ${inset('nether-view', 430, 250, 0, 'The Nether, under the roof')}
    </div>`,
  })),

  performance: card('performance', () => ({
    kicker: 'No Lag', title: 'Built not to lag',
    points: [
      '<b class="hi">Asynchronous chunk loading</b> on Paper and Folia',
      'Safe spots found ahead of time, so most teleports are instant',
      'Folia-safe: every chunk on its own region thread',
      'The console says why when a search finds nothing',
    ],
    art: `<div style="position:relative;height:780px;display:flex;flex-direction:column;justify-content:center;gap:22px">
      ${r.codePanel('Console · /rtpadmin info', r.consoleText('admin-info.txt').map(r.escapeHtml).join('\n'), { width: 850, size: 16, wrap: true })}
      ${r.stats([['Chunk loads', 'Async'], ['Ready spots', '3 per world'], ['Per loaded chunk', '4 columns'], ['Folia', 'Region-safe']])}
    </div>`,
  })),

  destinations: card('destinations', () => ({
    kicker: 'Destinations', title: 'Any world, any shape',
    points: [
      'As many destinations as you like, any world',
      'Square or circle, a centre, a minimum and maximum distance',
      'Heights, biomes, permission, cooldown and cost each',
      'The world border is always respected',
    ],
    art: `<div style="position:relative;height:780px">
      <div style="position:absolute;left:0;top:10px">${r.codePanel('config.yml', r.yaml(r.yamlSection('src/main/resources/config.yml', 'overworld')
        .filter((line) => !line.trim().startsWith('#') && !/minecraft:(cold|deep|frozen|lukewarm|warm)/.test(line))
        .map((line) => line.replace(/deny: \[minecraft:ocean,.*/, 'deny: [minecraft:ocean, minecraft:river, ...]'))
        .slice(0, 19)), { width: 640, size: 17 })}</div>
      ${inset('end-view', 420, 240, 0, 'The End')}
    </div>`,
  })),

  cooldowns: card('cooldowns', () => ({
    kicker: 'Cooldowns & Costs', title: 'Cooldowns, ranks and costs',
    points: [
      'Cooldowns are <b class="hi">saved</b> and survive restarts',
      'Shorter ones per rank: <code class="inline">donutrtp.cooldown.vip</code>',
      'Shared, or one per destination',
      '<b class="hi">Vault costs</b>, taken only once you land',
    ],
    art: `<div style="position:relative;height:780px">
      <div style="position:absolute;left:0;top:10px">${r.codePanel('config.yml', r.yaml(r.yamlSection('src/main/resources/config.yml', 'cooldown')
        .filter((line) => !line.trim().startsWith('#'))), { width: 560, size: 18 })}</div>
      ${inset('menu-cooldown-tip', 380, 230, 150, 'In the menu')}
      ${inset('cooldown-chat-line', 760, 70, 10, 'And in chat')}
    </div>`,
  })),

  integrations: card('integrations', () => ({
    kicker: 'Integrations', title: 'Plays well with others',
    points: [
      '<b class="hi">WorldGuard</b> zones and region avoidance',
      '<b class="hi">GriefPrevention</b> claims avoided',
      '<b class="hi">Vault</b> costs, <b class="hi">PlaceholderAPI</b>, <b class="hi">HeadDatabase</b>',
      'An API to cancel or move any teleport',
    ],
    art: (() => {
      const papi = ['%donutrtp_cooldown%', '%donutrtp_cooldown_nether%', '%donutrtp_zone%', '%donutrtp_zone_spawn_countdown%',
        '%donutrtp_zone_spawn_players%'].map((p) => `<span style="color:var(--gold-hi)">${p}</span>`).join('\n');
      const api = `<span style="color:var(--accent-hi)">@EventHandler</span>
<span style="color:var(--accent-hi)">public void</span> onRtp(RtpPreTeleportEvent event) {
    <span style="color:var(--accent-hi)">if</span> (event.getSource() == RtpSource.ZONE &amp;&amp; eventRunning())
        event.setLocation(arenaEntrance());
}`;
      const badge = (name) => `<span class="pill" style="margin:0 10px 12px 0"><i></i>${name}</span>`;
      return `<div style="position:relative;height:780px;display:flex;flex-direction:column;justify-content:center;gap:24px">
        <div style="display:flex;flex-wrap:wrap;width:820px">${['WorldGuard', 'GriefPrevention', 'Vault', 'VaultUnlocked', 'PlaceholderAPI', 'HeadDatabase'].map(badge).join('')}</div>
        ${r.codePanel('PlaceholderAPI', papi, { width: 560, size: 19 })}
        ${r.codePanel('Developer API', api, { width: 820, size: 18 })}
      </div>`;
    })(),
  })),

  upgrade: card('upgrade', () => ({
    kicker: 'Upgrading from 1.x', title: 'Upgrade in place',
    points: [
      'Warmup, cooldown, worlds, radius and menu items carried over',
      'The WorldGuard zone and cuboid zones become 2.0 zones',
      'Old files kept as <code class="inline">config-1.x.yml</code>',
      'Nothing to do but replace the jar',
    ],
    art: `<div style="position:relative;height:780px;display:flex;flex-direction:column;justify-content:center">
      ${r.codePanel('Console · first start of 2.0 on a 1.x server', r.consoleText('upgrade.txt').map(r.escapeHtml).join('\n'), { width: 850, size: 16, wrap: true })}
    </div>`,
  })),

  compatibility: card('compatibility', () => ({
    kicker: 'Compatibility', title: 'Tested on every version',
    points: [
      'Paper, Folia, Purpur, Spigot and Bukkit',
      `Minecraft ${r.RANGE}, one jar`,
      'Bots upgrade, teleport and share zones on each',
      'WorldGuard, GriefPrevention and Vault included',
    ],
    art: (() => {
      const data = r.compat();
      const targets = data ? data.targets : [];
      const tile = (t) => `<div class="panel" style="padding:12px 16px 10px;border-color:${t.ok ? 'rgba(167,123,255,.5)' : 'rgba(255,107,107,.6)'}">
        <div style="font-family:DoDisplay;font-weight:700;font-size:13px;letter-spacing:.16em;text-transform:uppercase;color:var(--dim)">${t.project}</div>
        <div style="font-family:DoDisplay;font-weight:800;font-size:28px;color:var(--hi);line-height:1.1">${t.version}</div>
        <div style="font-size:15px;color:${t.ok ? 'var(--mint)' : 'var(--red)'};font-weight:600;margin-top:3px">${t.ok ? '✔' : '✖'} ${t.passed}/${t.steps} · Java ${t.java}</div></div>`;
      return `<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;width:820px">${targets.map(tile).join('')}</div>
        <div style="margin-top:16px;font-size:18px;color:var(--dim)">DonutRTP ${data ? data.plugin : ''} · automated run of tools/smoke on ${data ? data.date : ''}</div>`;
    })(),
  })),
};

for (const [key, title] of Object.entries(HEADERS)) {
  assets[`header-${key}`] = () => ({ width: 1400, height: 120, transparent: true, html: r.header(title) });
}
module.exports = assets;
