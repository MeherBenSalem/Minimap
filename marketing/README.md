# Marketing assets: Remnants Minimap 2.0

Listing copy and artwork for CurseForge and Modrinth. Nothing here ships in the mod JARs.

## Listing

| File | Use |
| --- | --- |
| [`curseforge/description.md`](curseforge/description.md) | Full project description (Markdown; also works on Modrinth) |
| [`curseforge/summary.txt`](curseforge/summary.txt) | Short project summary (214 characters) |
| [`../CHANGELOG.md`](../CHANGELOG.md) | Release notes; the publish workflow uses the top section |

Image links in the description point at `raw.githubusercontent.com/MeherBenSalem/Minimap/main/marketing/images/...`, so they work once this folder is on `main`. To host on CurseForge instead, upload the images to the project gallery and replace each URL.

The publish workflow still refreshes the Modrinth page from `../CURSEFORGE.md`. Point it at `curseforge/description.md` (or copy this file over `CURSEFORGE.md`) to use the new listing there.

## Images

| File | Size | Use |
| --- | --- | --- |
| `images/banner.png` | 1800×600 | Description header |
| `images/icon.png` | 512×512 | Project avatar (the mod logo on the kit's dark background) |
| `images/card-cartography.png` | 1600×900 | Engraved water, relief and contours, fog of the unexplored |
| `images/card-minimap.png` | 1600×900 | The round minimap: rim letters, pinned markers, info lines |
| `images/card-hud.png` | 1600×900 | Round and square minimap side by side |
| `images/card-worldmap.png` | 1600×900 | The world map zoomed out, with a magnified close-up |
| `images/card-markers.png` | 1600×900 | The 30 emblems, the Grace list filters, automatic markers |
| `images/card-pillars.png` | 1600×900 | Pillars of light, name and distance labels, pillar colors |
| `images/card-settings.png` | 1600×900 | The settings screen: tabs and option descriptions |
| `images/card-performance.png` | 1600×900 | Read, draw, save, zoom out |
| `images/header-*.png`, `images/divider.png` | 1400 wide, transparent | Section headings for the description |
| `images/screenshots/*.jpg` | 1600×900 (`minimap-closeup.jpg` 820×940) | Card sources and gallery uploads |

There are no GIFs yet. They need a recording from a running client; see "Refreshing the screenshots".

### Screenshots

These are the in-game captures from `docs/curseforge/`, copied unedited. `minimap-closeup.jpg` is a 2× crop of the minimap in `hud-round.jpg`.

| File | Gallery | Used by |
| --- | --- | --- |
| `hud-round.jpg` | yes | card-hud, card-pillars (crop of the beacon label) |
| `hud-square.jpg` | yes | card-hud |
| `minimap-closeup.jpg` | yes | card-minimap |
| `settings.jpg` | yes | card-settings |
| `pillar-dusk.jpg` | no | banner, card-pillars (both cropped above the pillar's label) |
| `worldmap.jpg` | no | card-cartography (three crops), card-worldmap (magnifier), backdrop |
| `worldmap-far.jpg` | no | card-worldmap, card-cartography (fog crop) |
| `worldmap-list.jpg` | no | card-markers (filters only) |

The captures marked "no" show marker names borrowed from another game. The cards only use crops that leave those names out, and the magnifier on `card-worldmap.png` covers the names and tooltip in the middle of the zoomed-out map. Keep those files out of the gallery until they are recaptured with original marker names.

`card-settings.png` maps the darkest 6% of `settings.jpg` to black (an sRGB levels filter), so the dimmed world map behind the settings screen, and the marker names on it, drop out. The menu itself is unchanged.

## Regenerating the artwork

The cards, banner, icon and headers are HTML rendered with Playwright from the screenshots above, the mod's own GUI art and the fonts in `source/fonts/`:

```bash
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs                 # everything
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs banner icon     # selected assets
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs backdrop        # only the card background
```

- `VERSIONS` and `LOADERS` at the top of `source/render.cjs` set the versions and loader pills on the banner. Keep them in step with the compatibility table in the description.
- `source/backdrop.jpg` is rendered by the `backdrop` target: `worldmap.jpg`, blurred and darkened until nothing in it is legible.
- `source/art/` holds copies of the mod's art: `logo.png` (from `docs/icon.png`, made by `tools/generate_icon.py`), and `markers.png` and `cloud_corner.png` (from `26.2/common/src/main/resources/assets/remnantminimaps/textures/gui/`, made by `tools/generate_assets.py`). If the mod's art changes, copy the new files here and rerun the script. The emblems on `card-markers.png` are drawn from `markers.png` with the default tints from `MarkerIcon.java`.
- Crops of screenshots are done in HTML (`crop()` and `lens()` in the script), so the files in `images/screenshots/` stay untouched.

### Refreshing the screenshots

Launch a client with the mod, capture at 1600×900, and replace the files in `images/screenshots/`. Keep the same framing (or update the crop rectangles in `render.cjs`, which are in screenshot pixels) and rerun the script. Give markers original names, so every capture can go in the gallery.

## Fonts

The artwork uses Cinzel (display) and EB Garamond (text), both under the SIL Open Font License 1.1. `source/fonts/` holds subsets of the two fonts with their license texts, `OFL-Cinzel.txt` and `OFL-EBGaramond.txt`. The fonts are only used to render these images; the mod does not bundle them.

## Wording

Describe the style as soulslike. Do not use other games' names, logos or art in listing images or titles. Every feature and number in the listing comes from the 26.2 source and resources; check them there before changing the copy.
