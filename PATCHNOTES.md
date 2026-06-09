# DonutRTP Patch Notes

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
