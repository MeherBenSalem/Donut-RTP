![DonutRTP 2.0: The Zone Update](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/banner.png)

## Random teleport and RTP zones for survival servers, without the lag

**DonutRTP** is a free, open-source random teleport plugin for Paper, Folia, Purpur, Spigot and Bukkit, from
Minecraft **1.20.1 to 26.3**.

`/rtp` opens a small menu: pick the Overworld, the Nether, the End or any world you add, stand still for a few
seconds, and land somewhere safe in the wild. **RTP zones** at spawn do it without a command. Players walk onto the
pad, and everyone standing there when the countdown ends **lands together**.

**2.0 "The Zone Update"** is a full rewrite:
- group zones, made in game;
- any number of destinations;
- asynchronous chunk loading, with spots found ahead of time;
- claim and region avoidance and landing protection;
- saved cooldowns with rank tiers, Vault costs, PlaceholderAPI and a developer API.

Each release is tested on every supported Paper and Folia version with real clients.

![Two players on an RTP pad: the countdown runs, and they land together](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/gifs/zone.gif)

![divider](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/divider.png)

![The Menu](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-menu.png)

![Pick where you land](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-menu.png)

- **One item per destination**, as many as you like, in any slot of a one to six row menu.
- Each item shows **your cooldown** ("Available in 4m 12s"), **the cost**, or **a lock** when you lack the
  permission. It updates every second while the menu is open.
- `/rtp nether` skips the menu. Tab completion lists only the destinations you may use.
- Icons can be any item, a **HeadDatabase** head (`hdb-1234`), or a player head from a texture, a UUID or a name.

![Teleporting](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-teleporting.png)

![Stand still, then go](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-warmup.png)

1. Pick a destination. The warmup counts down on the action bar, a title, or both.
2. Walking off your block or taking damage cancels it. **Jumping in place never does.**
3. You land on solid ground, in open air, away from claims. You take **no fall damage** for five seconds and no
   damage at all for three.
4. A sound and particles mark the landing. Console commands can run too, for kits or starter quests.

Ops and anyone with `donutrtp.warmup.bypass` skip the warmup. `/rtp player <name> [destination]` lets staff send
someone with no warmup, cooldown or cost.

![RTP Zones](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-zones.png)

![Land together](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-zones.png)

- **Group zones**: one countdown for everyone on the pad. When it ends, everyone still inside lands together, a few
  blocks apart.
- **Minimum players**: the countdown waits for enough players and says how many more it needs.
- **Solo zones**: your own countdown starts when you walk in, like 1.x.
- The countdown shows on the **boss bar, title and action bar**. Each can be turned off per zone.
- **Per-zone cooldown and permission**. Players on cooldown are told how long is left, and join when it runs out.
- Hologram and scoreboard timers through PlaceholderAPI: `%donutrtp_zone_spawn_countdown%`,
  `%donutrtp_zone_spawn_players%`.

![Zones made in game](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-zone-admin.png)

Stand on one corner and type `/rtpadmin pos1`, stand on the other and type `/rtpadmin pos2`, then
`/rtpadmin zone create spawn overworld`. That's it. Using WorldGuard? `/rtpadmin zone create spawn overworld region
spawn_pad` uses a region instead. Every setting changes in game with `/rtpadmin zone set`, without a restart.

![Safe Landings](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-safety.png)

![Never into lava](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-safety.png)

- Never into water, lava, fire, magma, powder snow, cobwebs, berry bushes or cactus.
- Never into a tree, the void, or on top of the Nether roof.
- **Never inside a WorldGuard region or a GriefPrevention claim.**
- Always inside the **world border**, with a margin.
- **Biome rules** per destination. Oceans and rivers are denied in the Overworld by default.

![No Lag](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-performance.png)

![Built not to lag](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-performance.png)

Loading a chunk far from everything else is the expensive part of any random teleport.
- On **Paper and Folia**, DonutRTP loads chunks asynchronously. Chunks for a waiting player load first, and each
  loaded chunk is fully used before another one is loaded.
- A few **safe spots per destination are found ahead of time**, so most teleports start the moment the warmup ends.
- On **Folia**, every block, player and chunk is touched only from the region that owns it.
- On **Spigot**, at most one chunk loads per tick.
- When a search finds nothing, the console says why: no safe ground, denied biomes, claims, or a busy server.

![Destinations](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-destinations.png)

![Any world, any shape](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-destinations.png)

Add as many destinations as you like: a resource world, a "far lands" option for veterans, a VIP-only world. Each one
has its own:
- world and **square or circle** shape;
- centre (or the world spawn), **minimum and maximum distance**, and heights;
- biome allow and deny lists;
- permission, cooldown, cost and menu item.

![Cooldowns & Costs](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-cooldowns.png)

![Cooldowns, ranks and costs](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-cooldowns.png)

- **Cooldowns are saved** and survive restarts.
- One cooldown for every destination, or one each.
- **Shorter cooldowns for ranks**: with `ranks: { vip: 120, mvp: 60 }`, players with `donutrtp.cooldown.vip` wait
  two minutes and `donutrtp.cooldown.mvp` one. The lowest a player has wins.
- **Costs through Vault**, taken only once the player has landed, and refunded if the teleport fails.
- Bypass permissions for cooldowns, costs and the warmup.

![Integrations](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-integrations.png)

![Plays well with others](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-integrations.png)

All of these are optional:

| Plugin | What DonutRTP does with it |
| --- | --- |
| WorldGuard | Zones from regions; never lands in a region |
| GriefPrevention | Never lands in a claim |
| Vault (or VaultUnlocked) | Destination costs |
| PlaceholderAPI | `%donutrtp_cooldown%`, `%donutrtp_cooldown_<destination>%`, `%donutrtp_zone%`, `%donutrtp_zone_<name>_countdown%`, `%donutrtp_zone_<name>_players%` |
| HeadDatabase | Menu icons |

**For developers**:
- `RtpPreTeleportEvent` is cancellable and can change where a player lands.
- `RtpTeleportEvent` fires after landing.
- Both say which destination and what started the teleport: command, menu, admin or zone.

![Upgrading from 1.x](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-upgrade.png)

![Upgrade in place](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-upgrade.png)

Replace the jar and restart. DonutRTP upgrades your 1.x setup on the first start:
- **Your settings carry over**: warmup, cooldown, worlds, radius, minimum height, sounds and menu items.
- **Your zones carry over**: the WorldGuard zone becomes a zone named after its region, and each cuboid zone keeps
  its name and permission.
- The old files are kept as `config-1.x.yml` and `messages-1.x.yml`.

![Compatibility](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-compatibility.png)

![Tested on every version](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/card-compatibility.png)

| Server | Minecraft | Java |
| --- | --- | --- |
| Paper, Purpur and other Paper forks | 1.20.1 – 1.20.4 | 17 |
| Paper, Purpur and other Paper forks | 1.20.5 – 1.21.11 | 21 |
| Paper, Purpur and other Paper forks | 26.1 – 26.3 | 25 |
| Folia | 1.20.4 – 26.3 | 17 – 25 |
| Spigot, Bukkit | 1.20.1 – 26.3 | as your server |

One jar for all of them. Each release boots a 1.x install on Paper 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.8,
1.21.11, 26.1.2, 26.2 and 26.3 and Folia 1.20.4, 1.21.4, 1.21.11 and 26.3. On each one, bots run 22 checks: the
upgrade, the menu, warmup cancels, the Nether and the End, a two-player group zone, WorldGuard, GriefPrevention,
Vault and the admin commands. The console must stay free of errors.

![Commands & Permissions](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-commands.png)

| Command | Permission | |
| --- | --- | --- |
| `/rtp` | `donutrtp.use` (everyone) | The menu |
| `/rtp <destination>` | `donutrtp.use` and the destination's permission | Teleport straight away |
| `/rtp player <player> [destination]` | `donutrtp.admin` | Send someone (no warmup, cooldown or cost) |
| `/rtpadmin reload` | `donutrtp.admin` | Reload config, messages and zones |
| `/rtpadmin info` | `donutrtp.admin` | Platform, hooks, destinations, ready spots and zones |
| `/rtpadmin pos1`, `/rtpadmin pos2` | `donutrtp.zone.admin` | A zone's corners, where you stand |
| `/rtpadmin zone create <name> <destination> [region <id>]` | `donutrtp.zone.admin` | Make a zone |
| `/rtpadmin zone set <name> <key> <value>` | `donutrtp.zone.admin` | Change a zone |
| `/rtpadmin zone delete\|info\|list` | `donutrtp.zone.admin` | |

`donutrtp.zone.use` lets players use zones (everyone). `donutrtp.cooldown.bypass` and `donutrtp.cost.bypass` are
for ops. `donutrtp.warmup.bypass` is given to nobody by default. `/donutrtp` works as `/rtpadmin`.

![Questions](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-faq.png)

**Does it lag?** On Paper and Folia, chunks load off the main thread, and ready spots are prepared ahead of time.
Generating new terrain still costs CPU, as it does for every RTP plugin. Pre-generating your world makes every
teleport cheap.

**Can I add my own worlds?** Yes. Add a destination under `destinations:` in `config.yml` with the world's name, then
`/rtpadmin reload`.

**Do I need WorldGuard for zones?** No. Zones are boxes you select in game. WorldGuard regions work too.

**Will my 1.x setup break?** No. It is upgraded on the first start and the old files are kept.

**Folia?** Yes, every part of it, including group zones.

**"No safe spot was found"?** The console says why. Usually the area is mostly ocean, or claims cover it. A larger
radius helps.

![Gallery](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/header-gallery.png)

![The menu](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/screenshots/menu-overworld.jpg)

![The countdown on a group zone](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/screenshots/zone-countdown.jpg)

![Landed together](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/screenshots/zone-landed.jpg)

![A Nether landing](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/screenshots/nether-view.jpg)

![An End landing](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/screenshots/end-view.jpg)

![divider](https://raw.githubusercontent.com/MeherBenSalem/donut-rtp/main/marketing/images/divider.png)

**Discord**: [discord.gg/SAmd9fhNhr](https://discord.gg/SAmd9fhNhr) · **Source**:
[GitHub](https://github.com/MeherBenSalem/donut-rtp) (Apache 2.0) · **Support the project**:
[nightbeam.dev/support](https://nightbeam.dev/support)
