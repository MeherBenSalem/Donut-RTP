# DonutRTP

DonutSMP-style random teleport GUI with optional WorldGuard / cuboid RTP zones
for Bukkit, Spigot, Paper, Purpur, and Folia.

## Features

- `/rtp` GUI for Overworld, Nether, and End destinations
- Warmup countdown, cooldown, and safe-location search
- WorldGuard RTP zone (`/donutrtp zone …`) plus legacy cuboid zones
- Jump-tolerant zone countdown (vertical-only movement does not cancel)
- Optional HeadDatabase GUI icons
- Folia-safe scheduling
- Modrinth update check on startup (ops notified on join)
- Anonymous bStats metrics (chart ID 33560)

## Requirements

- Java 21+
- Minecraft **1.20.1 through 26.3** (including 1.21.x, 26.1.x, and 26.2)
- The plugin jar targets Java 21. Paper / Folia 26.x servers themselves require Java 25.
- Software: **Bukkit, Spigot, Paper, Purpur, or Folia**
- Optional: WorldGuard, HeadDatabase

## Installation

1. Download the jar from [Modrinth](https://modrinth.com/plugin/donut-rtp-and-rtp-zone) or CurseForge.
2. Place it in your `plugins` folder and restart.
3. Configure `plugins/DonutRTP/config.yml` and `messages.yml` as needed.

## Usage

- `/rtp` — open the RTP GUI
- `/donutrtp zone set|remove|info|reload` — manage the WorldGuard RTP zone (admin)

## Building

```bash
./gradlew.bat build
```

The shaded jar is written to `build/libs/DonutRTP-<version>.jar`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
Security reports: [.github/SECURITY.md](.github/SECURITY.md).

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and
[NOTICE](NOTICE).
