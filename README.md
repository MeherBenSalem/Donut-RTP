# DonutRTP

Random teleport and RTP zones for Paper, Folia, Purpur, Spigot and Bukkit, Minecraft **1.20.1 to 26.3**.

`/rtp` opens a small menu to pick a world, and RTP zones at spawn send players into the wild: alone, or together
with everyone standing on the pad when the timer runs out. Finding a spot never freezes the server.

**2.0 "The Zone Update"** is a full rewrite. See [DonutRTP-2.0.0-PatchNotes.md](DonutRTP-2.0.0-PatchNotes.md).

## Features

- **The menu**: one item per destination, showing your cooldown, the cost and whether you may use it.
  `/rtp <destination>` skips the menu.
- **No lag**: chunks load asynchronously on Paper and Folia, and a few safe spots per destination are found ahead of
  time, so most teleports start the moment the warmup ends.
- **Safe landings**: never in water, lava, fire, powder snow, a tree or the Nether roof. A few seconds without fall
  damage after landing.
- **Group RTP zones**: everyone in the zone when the countdown ends lands together, a few blocks apart. Set a minimum
  number of players. Solo zones start your own countdown when you walk in.
- **Zones made in game**: `/rtpadmin pos1`, `pos2`, `zone create`. You can also use a WorldGuard region. Countdowns
  show on the title, action bar and boss bar, each switchable per zone.
- **Any number of destinations**: each has its own world, square or circle shape, centre, minimum and maximum
  distance, heights, biome rules, permission, cooldown, cost and menu item. The world border is always respected.
- **Claims and regions avoided**: WorldGuard regions and GriefPrevention claims.
- **Cooldowns that survive restarts**: shared or per destination, with shorter ones per rank
  (`cooldown.ranks: { vip: 120 }` gives `donutrtp.cooldown.vip` a two-minute cooldown).
- **Costs** through Vault, taken only once you have landed.
- **PlaceholderAPI** placeholders for cooldowns and zone timers. HeadDatabase and custom head textures for menu
  icons.
- A **developer API**: the cancellable `RtpPreTeleportEvent` (change where the player lands) and `RtpTeleportEvent`.
- **Upgrading from 1.x is automatic**. Your config, the WorldGuard zone and the cuboid zones carry over, and the old
  files are kept.

## Requirements

- A server for Minecraft 1.20.1 to 26.3: Paper, Folia, Purpur, Spigot or Bukkit.
- Java 17 or newer, which is whatever your server needs: 17 for 1.20.1 to 1.20.4, 21 for 1.20.5 to 1.21.11, and 25
  for 26.x.
- Optional: Vault with an economy plugin, WorldGuard, GriefPrevention, PlaceholderAPI, HeadDatabase.

## Installation

1. Download `DonutRTP-2.0.0.jar` from [Modrinth](https://modrinth.com/plugin/donut-rtp-and-rtp-zone), CurseForge,
   or [`releases/`](releases/).
2. Put it in `plugins/` and restart.
3. Edit `plugins/DonutRTP/config.yml` (destinations and rules), `messages.yml` and `zones.yml`, then
   `/rtpadmin reload`.

## Commands

| Command | Permission | |
| --- | --- | --- |
| `/rtp` | `donutrtp.use` (everyone) | The menu |
| `/rtp <destination>` | `donutrtp.use` and the destination's own permission | Teleport straight away |
| `/rtp player <player> [destination]` | `donutrtp.admin` | Send someone, with no warmup, cooldown or cost |
| `/rtpadmin reload` | `donutrtp.admin` | Reload `config.yml`, `messages.yml` and `zones.yml` |
| `/rtpadmin info` | `donutrtp.admin` | Platform, hooks, destinations, ready spots and zones |
| `/rtpadmin pos1`, `/rtpadmin pos2` | `donutrtp.zone.admin` | Mark a zone's corners where you stand |
| `/rtpadmin zone create <name> <destination> [region <id>]` | `donutrtp.zone.admin` | From the corners, or a WorldGuard region |
| `/rtpadmin zone set <name> <key> <value>` | `donutrtp.zone.admin` | `destination`, `mode`, `countdown`, `min-players`, `spread`, `cooldown`, `cancel-on-move`, `permission`, `enabled`, `title`, `actionbar`, `bossbar`, `region`, `area` |
| `/rtpadmin zone delete\|info\|list` | `donutrtp.zone.admin` | |

`/donutrtp` is an alias of `/rtpadmin`.

Other permissions: `donutrtp.zone.use` (be teleported by zones, everyone), `donutrtp.cooldown.bypass` and
`donutrtp.cost.bypass` (ops), `donutrtp.warmup.bypass` (nobody by default).

## Placeholders

`%donutrtp_cooldown%`, `%donutrtp_cooldown_seconds%`, `%donutrtp_cooldown_<destination>%`, `%donutrtp_zone%`,
`%donutrtp_zone_<name>_countdown%`, `%donutrtp_zone_<name>_players%`. The design notes list what each one returns.

## Developer API

```java
@EventHandler
public void onRtp(RtpPreTeleportEvent event) {
    if (event.getSource() == RtpSource.ZONE && isEventRunning()) {
        event.setLocation(arenaEntrance());   // or event.setCancelled(true)
    }
}
```

`RtpPreTeleportEvent` (cancellable) fires once a safe spot is found and before anything is charged.
`RtpTeleportEvent` fires after the player has landed. Both give the destination id and the source: `COMMAND`,
`MENU`, `ADMIN` or `ZONE`.

## Building and testing

```bash
./gradlew build                     # build/libs/DonutRTP-<version>.jar, with the unit tests
./gradlew -p tools/smoke/fixture build
npm --prefix tools/smoke install
node tools/smoke/run.mjs            # every supported Paper and Folia version, with bots
```

[`tools/smoke/README.md`](tools/smoke/README.md) explains the bot matrix and [`docs/DESIGN-2.0.md`](docs/DESIGN-2.0.md)
the design.

## Support

- Discord: <https://discord.gg/SAmd9fhNhr>
- Issues: [GitHub Issues](https://github.com/MeherBenSalem/donut-rtp/issues)
- Support the project: <https://nightbeam.dev/support>

Anonymous usage statistics are sent to [bStats](https://bstats.org/plugin/bukkit/DonutRTP/33560); turn them off in
`plugins/bStats/config.yml`.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Contributions: [CONTRIBUTING.md](CONTRIBUTING.md),
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
