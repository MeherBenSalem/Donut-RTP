# Cross-version smoke matrix

Boots real Paper and Folia servers across the supported range and plays DonutRTP with
[mineflayer](https://github.com/PrismarineJS/mineflayer) bots over the network protocol. Every check goes through the
real plugin, real chunk generation, and the real WorldGuard, GriefPrevention and Vault.

| Target | Java | Bot protocol | Economy bridge |
| --- | --- | --- | --- |
| Paper 1.20.1, 1.20.4 | 17 | same version | Vault / VaultUnlocked |
| Paper 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.11 | 21 | same version | alternating |
| Paper 26.1.2, 26.2, 26.3 | 25 | 26.1 through ViaVersion + ViaBackwards | alternating |
| Folia 1.20.4, 1.21.4, 1.21.11, 26.3 | 17 / 21 / 25 | as above | VaultUnlocked |

WorldEdit, WorldGuard and GriefPrevention are added where Modrinth has a build for that version and loader: the
oldest release made for that game version, which runs on the Java of its time. GriefPrevention is Paper-only. The
steps that need them run only when DonutRTP reports that it hooked into them.

Each server starts from a **DonutRTP 1.5.2 install**: a 1.x `config.yml`, a WorldGuard zone and a cuboid zone. Then
it runs `scenario.mjs`:

1. The plugin enables on the right platform and upgrades the 1.x files. The upgrade keeps the old files, carries
   the values over, and turns both 1.x zones into 2.0 zones.
2. `Alice` opens `/rtp`. The menu shows the three destinations and the filler. She clicks the Overworld and lands
   after the warmup. The spot is checked: within the distance range, solid floor, open feet and head, not an ocean
   or river.
3. Her next `/rtp` is refused with the time left, and `%donutrtp_cooldown%` shows it.
4. `Bob` takes damage during the warmup, which cancels it. He then lands in the Nether: under the roof, on safe
   ground. His rank (`cooldown.ranks.mvp: 5`) makes his next cooldown 5 seconds instead of 30.
5. `Cara` is teleported away during her warmup, which cancels it. The console sends her to the End with
   `/rtp player`. Fall damage is blocked right after landing and back to normal after six seconds.
6. On servers the bots can walk on (no ViaBackwards), `Walker` walks off his block (cancel), then jumps in place (no
   cancel).
7. `Opal`, an op, makes a group zone in game: `pos1`, `pos2`, `zone create`, `zone set`. `Dan` steps on and is told
   to wait for one more player (action bar and placeholder). `Eve` steps on, and the boss bar counts down. Both land
   together, at most 9 blocks apart. Dan's next try is refused by the zone cooldown.
8. `Finn` uses the upgraded 1.x cuboid zone and the upgraded WorldGuard zone.
9. `Hal` cannot land in a destination covered by a WorldGuard region, or in one covered by a GriefPrevention claim.
   With avoidance turned off, he can.
10. `Ivy` is refused a destination without its permission, then let in. She is refused a paid destination with too
    little money, then charged exactly $50.
11. `/rtpadmin info` and `/rtpadmin zone list` answer on the console.
12. The console is scanned for plugin errors:
    - severe lines from the plugin;
    - stack frames in `io.nightbeam.donutrtp`;
    - linkage errors.

## Running it

```bash
./gradlew build
./gradlew -p tools/smoke/fixture build
npm --prefix tools/smoke install
node tools/smoke/run.mjs                          # all targets (about 35 minutes)
node tools/smoke/run.mjs paper-1.20.1 folia-26.3  # selected targets
```

JDKs, server jars and plugins are downloaded on first use and cached in `$RTPLAB_HOME` (default
`~/.cache/donutrtp-smoke`); what the DonutOrders lab already downloaded is reused. Servers listen on `$RTPLAB_PORT`
(default 25598). `RTPLAB_DEBUG=1` starts the servers with `-Ddonutrtp.debug=true`, which logs every step of every
search.

A JSON report is written to `$RTPLAB_HOME/reports/`. `node tools/smoke/compat.mjs` turns the newest report into
`marketing/source/compat.json` for the compatibility card.

`fixture/` is a test-only plugin, never to be installed on a real server. It provides:
- an in-memory Vault economy;
- a listener that prints DonutRTP's API events;
- console helpers under `rtplab`: `money`, `balance`, `pos`, `tp`, `damage`, `fall`, `perm`, `set`, `yaml`, `file`,
  `region`, `claim`, `platform`, `surface` and `sudo`.
