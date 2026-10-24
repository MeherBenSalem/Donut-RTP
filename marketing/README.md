# Marketing assets: 2.0 "The Zone Update"

Listing copy and artwork for CurseForge, Modrinth and Hangar. Nothing here ships in the plugin jar.

## Listing

| File | Use |
| --- | --- |
| [`curseforge/description.md`](curseforge/description.md) | Full project description (Markdown; also works on Modrinth and Hangar) |
| [`curseforge/summary.txt`](curseforge/summary.txt) | Short project summary (224 characters) |
| [`images/icon.png`](images/icon.png) | Project avatar |
| [`../DonutRTP-2.0.0-PatchNotes.md`](../DonutRTP-2.0.0-PatchNotes.md) | File changelog the publish workflow uploads |

Image links in the description point at `raw.githubusercontent.com/MeherBenSalem/Donut-RTP/main/marketing/images/...`,
so they work once this folder is on `main`. To host on CurseForge instead, upload the images to the project gallery and
replace each URL.

The description runs in this order:
1. the menu, teleporting and zones;
2. safe landings, performance and destinations;
3. cooldowns and costs, integrations and upgrading from 1.x;
4. compatibility, commands and permissions, the FAQ, a gallery and the links.

The artwork shares the pixel donut, fonts and dark backdrop of DonutOrders and DonutAuctionHouse. DonutRTP's donut
has violet icing.

## Adding a Minecraft version

The supported range comes from [`release/supported-minecraft.json`](../release/supported-minecraft.json), which the
publish workflow also uses. The banner and the compatibility card read it, so a new version needs no artwork edits:

1. Add the version to `release/supported-minecraft.json` and to `MATRIX` in
   [`tools/smoke/run.mjs`](../tools/smoke/run.mjs).
2. Run the smoke matrix, then `node tools/smoke/compat.mjs` to refresh `source/compat.json`.
3. Rerun the renderer for `banner compatibility`. If the Java requirement changes, also edit the compatibility table
   in `curseforge/description.md`.

A new release name or number goes in `RELEASE` in `source/render.cjs` and in the first lines of the description.

## Images

| File | Size | Use |
| --- | --- | --- |
| `images/banner.png` | 1800×600 | Description header: the menu, "2.0 · The Zone Update", platforms and versions |
| `images/icon.png` | 512×512 | Project avatar: the pixel donut with a gold "RTP" tag |
| `images/card-*.png` | 1600×900 | 11 feature cards: menu, warmup, zones, zone-admin, safety, performance, destinations, cooldowns, integrations, upgrade, compatibility |
| `images/header-*.png` | 1400×120, transparent | 13 section headings for the description |
| `images/divider.png` | 1400×40, transparent | Section divider |
| `images/gifs/zone.gif`, `images/gifs/menu.gif` | 640×360 | A group zone countdown and landing; the menu, warmup and teleport |
| `images/screenshots/*.jpg` | 1920×1080 | Gallery uploads and card art |

### How the screenshots were captured

Every screenshot and GIF is an unedited capture of the **vanilla Minecraft 1.21.11 client**, launched with
portablemc, on a **Paper 1.21.11** server running DonutRTP 2.0.0.
- The client ran in a 1920×1080 Xvfb display with software rendering (Mesa llvmpipe) at GUI scale 3, with the
  Bright brightness setting.
- It was driven by [`tools/marketing/capture.mjs`](../tools/marketing/capture.mjs) through console commands and XTEST
  mouse and keyboard input.

- **The world** is seed 8675309 on peaceful, at noon. An RTP pad (purpur, amethyst and end rods) was built in the
  nearest cherry grove with `/fill`, and the zone was made on it with `/rtpadmin pos1`, `pos2` and `zone create`.
- **Configuration**: the shipped defaults, except:
  - a 3-second warmup that also shows as a title;
  - a $250 cost on the Nether;
  - a permission (`rtp.end`) on the End, which Moonbeam lacks, so the End shows as locked.
  - The economy is the smoke-test fixture's in-memory Vault economy.
- **Real actions**: every teleport in the shots happened through the plugin.
  - Moonbeam clicked the Overworld in the menu and landed after the warmup.
  - The console sent Moonbeam to the Nether and the End with `/rtp player`.
  - Kiri, a mineflayer bot, stepped onto the pad with Moonbeam. The group countdown ran and both landed together.
  - After each landing the camera was turned with `/tp`: levelled to look around the landing, or aimed at Kiri.
  - Moonbeam had hidden fire resistance, so a stray lava flow could not end a capture run.
- **The upgrade card** shows the real console of a separate server started with DonutRTP 1.5.2 files.
- **The GIFs** are real-time recordings (x11grab, 12 fps, cropped to the centre 1280×720 and scaled to 640×360).
  Each one cuts at the teleport to a 3-second clip recorded at the landing once the client had drawn the terrain
  (software rendering takes about 40 seconds).
- **Two capture runs** were used, because every landing is random. The menu GIF and the Overworld and End landing views
  come from one run; everything else comes from the other.

## Regenerating the artwork

```bash
./gradlew build
./gradlew -p tools/smoke/fixture build
RTPLAB_PORT=25600 node tools/marketing/capture.mjs    # screenshots, GIFs and console text (about 10 minutes)
cp marketing/images/screenshots/scenery.jpg marketing/source/backdrop.jpg
python3 tools/marketing/crops.py                       # crop boxes of the menus and tooltips
node tools/smoke/compat.mjs                            # the compatibility card's data
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs   # every image, or name the ones to render
```

`render.cjs` needs Playwright with Chromium. The fonts are Outfit and Inter (SIL Open Font License 1.1, in
`source/fonts/`).
