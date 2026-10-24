# DonutRTP 2.0: The Zone Update

Design notes for the 2.0 rewrite. 1.5.2 is the baseline. The goal stays a DonutSMP-style random teleport: a small
GUI to pick a world, and RTP zones at spawn that send you out into the wild, now with friends.

## Goals

1. **No lag, ever.** Finding a spot must never generate or load chunks on the main or region thread. 1.5.2 loads one
   chunk synchronously per attempt (up to 60 per `/rtp`), which stalls a busy server.
2. **Group RTP zones**, the way DonutSMP's spawn pads work: everyone standing in the zone when the timer runs out
   lands together.
3. **The features players and admins expect** from current RTP plugins (see "Market"), with nothing that needs a
   database or a proxy.
4. **One jar for Bukkit, Spigot, Paper, Purpur and Folia, Minecraft 1.20.1 to 26.3**, proven on real servers with
   bots.
5. **A painless upgrade**: 1.5.x `config.yml`, the WorldGuard zone and the cuboid zones carry over.

## Market

What current RTP and RTP-zone plugins offer, and what reviews keep asking for:

| Expectation | 1.5.2 | 2.0 |
| --- | --- | --- |
| No lag: async chunk loading, locations found ahead of time | sync chunk loads on the main thread | async chunk loads on Paper/Folia; a small pool of ready spots per destination |
| Group zones: everyone in the zone lands together | no | GROUP zones with a shared countdown, minimum players and a spread |
| Several zones, without WorldGuard | one WorldGuard region, or cuboids in `config.yml` | any number of zones: a WorldGuard region or a cuboid selected in game |
| Countdown on title, action bar or boss bar | title + action bar | each one switchable, per zone |
| Hologram/scoreboard timers | no | PlaceholderAPI placeholders for zone timers and player counts |
| Avoid claims and regions | no | WorldGuard regions and GriefPrevention claims are skipped |
| World border, min/max radius, centre, circle or square | radius only, square, centred on 0,0 | all of them, and the border is always respected |
| Biome rules | no | allow and deny lists per destination |
| Landing safety: no fall damage, short invulnerability | no | yes |
| Cooldowns that survive a restart, per rank | memory only, one value | saved to disk, per destination or shared, permission tiers |
| Cost per teleport | no | Vault, optional, charged only when you land |
| Any number of destinations (custom worlds) | three fixed world types | any number, each with its own item, rules and permission |
| `/rtp <world>`, `/rtp player <name>` | GUI only | both, with tab completion |
| Plain Spigot | crashes on the warmup (Adventure-only action bar) | works: legacy and hex colours through the Bungee chat API |
| Folia thread safety | zone state in plain hash maps touched from region threads | concurrent state, region-correct scheduling |
| Developer API | no | cancellable `RtpPreTeleportEvent`, `RtpTeleportEvent` |

Out of scope: cross-server RTP (needs a proxy and a database), Towny/Lands hooks (no free test servers to verify).

## Platform

- Compiled against the Paper 1.20.1 API with Java 17 bytecode, `api-version: 1.20`, `folia-supported: true`. 1.5.2
  shipped Java 21 bytecode, which a 1.20.1-1.20.4 server on Java 17 cannot load.
- Paper-only API (`getChunkAtAsync`, `teleportAsync`, region schedulers) is used only when present. Spigot falls back
  to loading one chunk per tick on the main thread, spread out so it never stalls.
- Text goes through one helper: `&` and `&#RRGGBB` colours. Action bars use Adventure where the server has it and the
  Bungee chat API on Spigot; titles and boss bars use Bukkit. Nothing Adventure-only is loaded on Spigot.

## Destinations

`config.yml` → `destinations`: any number, keyed by id (`overworld`, `nether`, `end` by default).

| Key | Meaning |
| --- | --- |
| `world` | world name |
| `shape` | `SQUARE` or `CIRCLE` |
| `center` | `{x, z}`, or `spawn` for the world spawn |
| `min-radius`, `max-radius` | ring to pick from (blocks) |
| `min-y`, `max-y` | allowed landing heights |
| `biomes.allow`, `biomes.deny` | biome keys such as `minecraft:ocean`. Tags (`#minecraft:is_ocean`) are not supported: Bukkit has no biome tags before 1.21, so a tag is skipped with a warning |
| `permission` | needed to use it (empty: everyone) |
| `cooldown` | seconds, overriding the global value (-1: use global) |
| `cost` | Vault money per teleport (0: free) |
| `gui` | `slot`, `material` (or `hdb-<id>`), `head`, `name`, `lore` |

The 1.5.x `worlds.{overworld,nether,end}` + `gui.items.*` are read into the three default destinations once.

## Finding a spot

```
candidate (x, z): shape + ring + centre, inside the world border (8-block margin), evenly spread over the area
load the chunk: Paper/Folia getChunkAtAsync(generate = true, urgent when a player waits); Spigot: one sync load per tick
on the owning thread, for up to 4 columns of that chunk: find the standing height (surface heightmap ignoring
leaves; the Nether scans down from under the roof), check floor/feet/head and banned blocks, the y range, the
biome rules, then WorldGuard regions and GriefPrevention claims
```

Generating one chunk far from everything else is the expensive part, so each loaded chunk is used fully (up to 4
columns) before another is loaded. A player's search runs two chunks at a time; background work runs one.

A small pool (`search.pool-size`, default 3) of ready spots is kept per destination and refilled in the background
(`search.refill-seconds`), only while players are online and never while a player is waiting for a search. A pooled
spot is re-checked before use; a stale one is discarded.

A search gives up after `search.max-attempts` columns or 40 seconds. When a player's search finds nothing, the console
says why (no safe ground, denied biomes, claims and regions, or chunks still generating), at most every five minutes per
destination.

## Teleporting

warmup (cancel on horizontal move or damage, both switchable) → cost check → `RtpPreTeleportEvent` →
`teleportAsync` / `teleport` → landing protection (no fall damage for `landing.no-fall-damage-seconds`, full
invulnerability for `landing.invulnerable-seconds`) → sound, particles → cooldown → `RtpTeleportEvent`.
Money is taken only after the teleport succeeds.

Cooldowns live in `plugins/DonutRTP/data/cooldowns.yml` (saved on change, debounced, and on disable). Shared across
destinations by default (`cooldown.shared`), rank tiers under `cooldown.ranks` (`vip: 120` is the permission
`donutrtp.cooldown.vip`; lowest wins),
`donutrtp.cooldown.bypass` skips them.

## Zones

Stored in `zones.yml`, managed with `/rtpadmin zone ...`.

| Key | Meaning |
| --- | --- |
| `area` | `world` plus either `region: <WorldGuard id>` or `min: [x,y,z]` and `max: [x,y,z]` (both corners included) |
| `enabled` | off keeps the zone in the file but inactive |
| `destination` | destination id |
| `mode` | `INDIVIDUAL`: your own countdown starts when you walk in. `GROUP`: one shared countdown; everyone inside when it ends lands together |
| `countdown` | seconds |
| `min-players` | GROUP: the countdown only runs with at least this many players inside |
| `spread` | GROUP: players land within this many blocks of each other |
| `cooldown` | seconds before the same player can use this zone again (0: none) |
| `permission` | needed to use this zone, on top of `donutrtp.zone.use` (empty: none) |
| `display` | `title`, `actionbar`, `bossbar` on/off |
| `cancel-on-move` | INDIVIDUAL: walking off your block cancels (jumping never does) |

Leaving a zone always cancels your part of it. Players without `donutrtp.zone.use` are ignored. Players on cooldown
are told how long is left, and join the zone when it runs out if they are still standing in it. A GROUP zone shows
"teleporting" until every member has moved, so nobody sees "waiting for players" in between. The cuboid's top gets
two extra blocks so jumping never counts as leaving.

Migration: the 1.5.x WorldGuard zone becomes a zone named after its region (INDIVIDUAL, same countdown, cooldown and
cancel-on-move); each 1.5.x cuboid becomes a zone with its own name, its own permission and corners rounded to blocks.
Zones that were off in 1.5.x stay off (`enabled: false`).

## Commands and permissions

| Command | Permission | |
| --- | --- | --- |
| `/rtp` | `donutrtp.use` | the GUI |
| `/rtp <destination>` | `donutrtp.use` + the destination's permission | direct |
| `/rtp player <player> [destination]` | `donutrtp.admin` | send someone (no warmup, cooldown or cost) |
| `/rtpadmin reload` | `donutrtp.admin` | reload `config.yml`, `messages.yml`, `zones.yml` |
| `/rtpadmin info` | `donutrtp.admin` | destinations, pools, zones, hooks |
| `/rtpadmin pos1` / `pos2` | `donutrtp.zone.admin` | cuboid corners at your feet |
| `/rtpadmin zone create <name> <destination> [region <id>]` | `donutrtp.zone.admin` | from the selection or a WorldGuard region |
| `/rtpadmin zone delete|info|list` , `zone set <name> <key> <value>` | `donutrtp.zone.admin` | |

`/donutrtp` is an alias of `/rtpadmin`. `/rtp reload` and `/rtp zone ...` are passed on to `/rtpadmin` unless a
destination has that name.

## Placeholders (PlaceholderAPI)

| Placeholder | Value |
| --- | --- |
| `%donutrtp_cooldown%` | time left on the /rtp cooldown (the longest one when each destination has its own), or `placeholder-ready` from messages.yml |
| `%donutrtp_cooldown_seconds%` | the same in seconds |
| `%donutrtp_cooldown_<destination>%`, `%donutrtp_cooldown_seconds_<destination>%` | one destination |
| `%donutrtp_zone%` | the zone the player stands in |
| `%donutrtp_zone_<name>_countdown%` | a GROUP zone's countdown, empty when it is not counting |
| `%donutrtp_zone_<name>_players%` | players in the zone |

## Tests

Unit tests for the Minecraft-free parts: sampling (range and even spread), ring/shape/border maths, cooldown tiers and
storage, config reading and the 1.x migration, zones.yml round trips, durations, placeholders and versions.

A bot matrix (`tools/smoke/`, see its README) boots Paper 1.20.1-26.3 and Folia 1.20.4-26.3 from a 1.x install and
checks the upgrade, the GUI, direct `/rtp`, cooldowns and placeholders, warmup cancels (damage, being moved, walking
and not jumping), the Nether and the End, `/rtp player`, landing protection, a GROUP zone made in game with two bots,
the upgraded cuboid and WorldGuard zones, WorldGuard and GriefPrevention avoidance, permissions, Vault costs and the
admin commands, then scans the console for errors.
