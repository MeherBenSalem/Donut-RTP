# DonutRTP Patch Notes

## 1.5.2

### Platform
* Minecraft **26.3** support (Paper API `26.3.build.49-alpha`, the latest published 26.3 API artifact)
* Boot-tested on Paper **26.3 ALPHA build #133**, Paper 26.2 build 129, Paper 1.20.1 build 196, and Folia 26.2 build 7
* Folia 26.3 is not published yet (Paper Fill API returns 404)
* Existing support for 1.20.1–26.2, Folia, Bukkit, Spigot, and Purpur is unchanged (`api-version: 1.20`, `folia-supported: true`)

### Upgrade Notes
1. Replace the jar with **1.5.2**. Keep `plugins/DonutRTP/`.
2. Paper / Folia 26.x servers require Java 25; the plugin jar still runs on Java 21 for 1.20.5–1.21.x.

---

## 1.5.1

### Bug Fixes
* **RTP zone no longer cancels when you jump.** Countdown tracks the height where it started; jumping in place keeps it running. Walking out still cancels.
* **"Failed to find a safe location" is far less frequent.** Surface finder scans from the true surface (ignoring leaf canopies) and lands feet on solid ground.

### Platform
* bStats metrics (plugin ID **33560**)
* Modrinth update check for [donut-rtp-and-rtp-zone](https://modrinth.com/plugin/donut-rtp-and-rtp-zone) with console warning and `donutrtp.admin` join notify (`update-check.*`)
* Confirmed support matrix: Bukkit / Spigot / Paper / Purpur / Folia on Minecraft **1.20.1–26.2**
* Apache-2.0 OSS cleanup (LICENSE, NOTICE, README, contributor docs)

### Behaviour Notes
* `rtp-zone.countdown.cancel-on-move` only reacts to horizontal movement. Jumping never counts as moving.

### Upgrade Notes
1. Replace the jar with **1.5.1**. Keep `plugins/DonutRTP/`.
2. Optional new keys under `update-check` are backfilled by defaults if missing.

---

## 1.5.0

Store release covering early zone/safe-location work. Prefer **1.5.1** for the complete jump-tolerant countdown, safer landings, bStats, and update check.

---

## 1.4.0

### New Features
* **WorldGuard RTP zone** — configure an existing WorldGuard region as an RTP pad
  * `/donutrtp zone set <region>` or `/donutrtp zone set <world> <region>`
  * `/donutrtp zone remove`, `/donutrtp zone info`, `/donutrtp zone reload`
  * Trigger on region enter (ENTER / BOTH); no re-trigger while remaining inside
  * Zone-specific cooldown, action-bar countdown, and messages
* WorldGuard is an **optional softdepend** — the plugin starts without it and only disables region zones

### Configuration
* New `rtp-zone` section in `config.yml` (enabled by default; region empty until set)
* Legacy `rtp-zones` cuboid zones still work when no WorldGuard region is configured

### Permissions
* `donutrtp.zone.admin` — manage zone commands (default: op)
* `donutrtp.cooldown.bypass` — bypass cooldown when `rtp-zone.cooldown.bypass-enabled` is true

### Commands
* `/rtp` alias: `donutrtp`
* `/donutrtp zone set|remove|info|reload`

### Notes
* INTERACT trigger mode disables region-entry RTP only; the `/rtp` GUI remains available
* There is still no physical in-world RTP block/head interact listener

---

## 1.3.0

### New Features
* RTPZone mechanic — configurable cuboid zones that start a title/subtitle countdown on entry and random-teleport players when it completes

### Configuration
* New `rtp-zones` section in `config.yml` (disabled by default)
* Per-zone: world, center, half-size (X/Y/Z), countdown-seconds, world-type, optional permission

### Permissions
* `donutrtp.zone.use` — allows triggering RTP zones (default: true)

---

## 1.2.1

### Bug Fixes
* Fixed frequent "Failed to find a safe location" by loading chunks before checks, using world-aware height scanning (Nether cave scan), and allowing passable decorative blocks at feet/head

### Configuration
* Default `cooldown-seconds` changed from 30 to 300
* Default `max-attempts` increased from 30 to 60
* Existing servers: update `cooldown-seconds: 300` in your config.yml to apply the new cooldown

---

## 1.2.0

### Added
- **HeadDatabase ID support** — GUI items can use a HeadDatabase head via `material: "hdb-<id>"` (requires HeadDatabase plugin). Existing material and `head` block formats are unchanged.
- **Action bar cooldown sound** — Optional sound played each second during the warmup countdown (`actionbar-cooldown` in `config.yml`). Disabled by default for backwards compatibility.
- **Instant teleport option** — Set `instant-teleport: true` to skip the warmup countdown and teleport immediately after a safe location is found.

### Configuration
- New `instant-teleport` key in `config.yml` (default: `false`)
- New `actionbar-cooldown` section in `config.yml` (see inline comments for examples)
- HeadDatabase example under `gui.items` in `config.yml`
- Missing config keys are backfilled with sensible defaults on reload

---

## 1.1.0

### Added
- **Configurable GUI items** — Per-world RTP buttons (Overworld, Nether, End) can be customized in `config.yml`:
  - Slot, display name, and lore
  - Any valid Minecraft material as the icon
  - Custom player heads via texture hash, full base64 value, UUID, or player name
- **Configurable teleport sound** — Play a sound on successful RTP with adjustable volume and pitch (`teleport-sound` in `config.yml`)
- **Action bar warmup countdown** — Remaining seconds are shown on the action bar during the teleport warmup (configurable via `countdown-actionbar` in `messages.yml`)

### Changed
- Warmup countdown no longer spams chat each second; the "don't move" warning still appears once in chat
- Invalid materials, player heads, sounds, or GUI slots log a console warning and fall back to defaults

### Configuration
- New `gui.items` section in `config.yml` (see inline comments for examples)
- New `teleport-sound` section in `config.yml`
- New `countdown-actionbar` key in `messages.yml`

---

## 1.0.1

- Folia compatibility improvements for async teleport handling

## 1.0.0

- Initial release: DonutSMP-style RTP GUI for Paper and Folia
