# DonutRTP 2.0.0: The Zone Update

A full rewrite of DonutRTP. One jar runs on Paper, Folia, Purpur, Spigot and Bukkit, Minecraft 1.20.1 to 26.3, and
your 1.x setup upgrades itself on the first start.

## New

### Group RTP zones
- **GROUP zones**: one countdown for everyone on the pad. When it ends, everyone still inside lands together, a few
  blocks apart (`spread`).
- **Minimum players**: the countdown waits for `min-players`, and the zone shows how many more it needs.
- **INDIVIDUAL zones** still work like 1.x: your own countdown starts when you walk in.
- **Any number of zones**, made in game. Mark two corners with `/rtpadmin pos1` and `/rtpadmin pos2`, then run
  `/rtpadmin zone create <name> <destination>`. You can use a WorldGuard region instead
  (`... region <id>`).
- Every setting changes with `/rtpadmin zone set <name> <key> <value>`, without a restart.
- **Countdown on the title, action bar and boss bar**, each switchable per zone.
- **Per-zone cooldown and permission**. Players on cooldown are told how long is left, and join the zone when it
  runs out.
- Jumping never counts as leaving a zone.

### Destinations
- **Any number of destinations**, not just Overworld, Nether and End. Each has its own:
  - world, square or circle shape, centre (or the world spawn), minimum and maximum distance, and heights;
  - biome allow and deny lists (oceans and rivers are denied in the Overworld by default);
  - permission, cooldown, Vault cost and menu item.
- **The world border is always respected**, with a margin.
- `/rtp <destination>` teleports without the menu, with tab completion.
- `/rtp player <player> [destination]` lets admins send someone, with no warmup, cooldown or cost.

### The menu
- Each item shows the player's cooldown ("Available in 4m 12s"), the cost, and a lock when they lack the permission.
- It updates every second while open.
- Icons can be any item, a HeadDatabase head (`hdb-1234`), or a player head from a texture, UUID or name.

### Safety
- **Landing protection**: no fall damage for 5 seconds and no damage at all for 3 seconds after landing (both
  configurable).
- **Claims and regions avoided**: never land inside a WorldGuard region or a GriefPrevention claim.
- **Never land somewhere unsafe**: water, lava, fire, magma, powder snow, cobwebs, berry bushes, cactus, the Nether
  roof, the void, or a tree.

### Cooldowns, costs and ranks
- **Cooldowns are saved to disk** and survive restarts.
- Cooldowns can be shared or per destination.
- **Shorter cooldowns per rank**: `cooldown.ranks: { vip: 120 }` gives players with `donutrtp.cooldown.vip` a
  two-minute cooldown. The lowest one a player has wins.
- **Costs through Vault**, taken only once the player has landed, and refunded if the teleport fails.
- Bypass permissions: `donutrtp.cooldown.bypass`, `donutrtp.cost.bypass` and `donutrtp.warmup.bypass`.

### Warmup
- Taking damage cancels the warmup (it can be turned off).
- Being teleported away by something else also counts as moving.
- The countdown can show on the action bar, a title or both, with an optional tick sound.

### Integrations
- **PlaceholderAPI**:
  - `%donutrtp_cooldown%` and `%donutrtp_cooldown_seconds%`;
  - `%donutrtp_cooldown_<destination>%`;
  - `%donutrtp_zone%`;
  - `%donutrtp_zone_<name>_countdown%` and `%donutrtp_zone_<name>_players%`, for holograms and scoreboards.
- **Developer API**:
  - `RtpPreTeleportEvent` is cancellable and can change where the player lands;
  - `RtpTeleportEvent` fires after landing;
  - both carry the destination and the source (command, menu, admin or zone).
- **Landing commands**: console commands run after each teleport, with `{player}`, `{world}`, `{x}`, `{y}`, `{z}`
  and `{destination}`.

### Admin
- `/rtpadmin info` shows the platform, the hooks, each destination with its ready spots, and the zones.
- **A typo never wipes your setup**. If `config.yml` or `zones.yml` is not valid YAML, `/rtpadmin reload` says
  where the mistake is and keeps the previous settings. `zones.yml` is never overwritten while it cannot be read.
- When a player's search finds nothing, the console says why, at most every five minutes per destination:
  - no safe ground;
  - denied biomes;
  - claims and regions;
  - or chunks still generating on a busy server.

## Fixed
- **Lag spikes on `/rtp`**. 1.x loaded up to 60 chunks one after another on the main thread for every teleport. 2.0
  loads them asynchronously on Paper and Folia, uses each loaded chunk fully, and keeps a few spots per destination
  ready in advance. Spigot loads at most one chunk per tick.
- **1.20.1 to 1.20.4 servers on Java 17 could not load 1.5.x**: it was built for Java 21. 2.0 is built for Java 17.
- **Spigot errors on the warmup**: the action bar used a Paper-only API. Spigot now uses its own chat API.
- **Folia**: zone state was shared between region threads without protection. Every player, block and chunk is now
  touched only from the thread that owns it.

## Changed
- Admin commands moved to `/rtpadmin`; `/donutrtp` is an alias, so `/donutrtp reload` still works. Zone commands
  have a new form: `/rtpadmin zone create|set|delete|info|list`.
- Zones live in `zones.yml`. Messages use `{placeholders}` (1.x `%placeholders%` still work).
- New permissions:
  - `donutrtp.zone.use` (everyone) and `donutrtp.zone.admin` (ops);
  - `donutrtp.cost.bypass` (ops) and `donutrtp.warmup.bypass` (nobody).

## Upgrading from 1.x
1. Replace the jar and start the server. Keep `plugins/DonutRTP/`.
2. DonutRTP upgrades your files on the first start and logs what it did:
   - `config.yml` becomes the 2.0 layout: warmup, cooldown, attempts, sounds, worlds, radius, minimum height and menu
     items carry over.
   - The 1.x WorldGuard zone becomes a zone named after its region. Each 1.x cuboid zone becomes a zone with its own
     name and permission. Zones that were off stay off.
   - `messages.yml` is replaced by the 2.0 messages, keeping your prefix.
   - The old files are kept as `config-1.x.yml` and `messages-1.x.yml`.
3. The new defaults apply on top:
   - a 300-block minimum distance (capped at a quarter of your radius);
   - ocean and river biomes denied in the Overworld;
   - landing protection;
   - three ready spots per destination.

## Tested
- 22 unit tests.
- A bot matrix boots a 1.x install on Paper 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2 and
  26.3, and Folia 1.20.4, 1.21.4, 1.21.11 and 26.3. On each server it runs 22 checks, from the upgrade and the menu
  to group zones, WorldGuard, GriefPrevention and Vault, then scans the console for errors.
