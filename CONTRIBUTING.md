# Contributing

Thanks for contributing to DonutRTP.

## How to contribute

1. Fork the repository and create a feature branch from `main`.
2. Make focused changes with clear commit messages.
3. Build and run the unit tests: `./gradlew build` (`gradlew.bat build` on Windows).
4. For changes to teleporting, zones or platform code, run the bot matrix on at least one Paper and one Folia
   version: `node tools/smoke/run.mjs paper-1.21.11 folia-26.3` (see [tools/smoke/README.md](tools/smoke/README.md)).
5. Open a pull request describing the change and how you verified it.

## Guidelines

- Prefer small, reviewable PRs.
- Match existing naming and package layout. Touch players, blocks and chunks only through `platform.Scheduler`, and
  Paper-only API only behind `platform.Platform`, so Folia and Spigot keep working.
- Do not commit build outputs, secrets, or IDE-only files.
- Unless stated otherwise, contributions are licensed under the Apache License 2.0.

## Issues

Use GitHub Issues for bugs and feature requests. Include server software
(Paper/Folia/etc.), Minecraft version, plugin version, and relevant logs/config.
