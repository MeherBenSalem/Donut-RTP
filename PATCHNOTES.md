# DonutRTP Patch Notes

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
