# Remnants Minimap

A client-side Minecraft minimap and world map in the style of the Elden Ring map:
hand-drawn parchment cartography, a gilded round minimap, golden Sites of Grace,
beacons with pillars of light, and a full world map you can explore and annotate.
Formerly known as Odyssey Map.

## Supported versions

These are the only supported targets (the NightBeam convention): 8 jars per release, all with the same version.

| Minecraft | Loaders | Java | Folder |
| --- | --- | --- | --- |
| 1.20.1 | Forge, Fabric | 17 bytecode (build on 21) | `1.20.1/{common,forge,fabric}` |
| 1.21.1 | NeoForge, Fabric | 21 | `1.21.1/{common,neoforge,fabric}` |
| 26.2 | NeoForge, Fabric | 25 | `26.2/{common,neoforge,fabric}` |
| 26.3 | NeoForge, Fabric | 25 | `26.3/{common,neoforge,fabric}` |

Every root is an independent Gradle build with the same features, mod id and data formats, so maps,
markers and settings carry over between Minecraft versions. Fabric needs Fabric API on every version.

## Features

**Cartography**
- Terrain redrawn as an aged parchment chart: muted palette, relief shading, ink contour
  lines, engraved water, ink coastlines, building footprints and roads.
- Unexplored land fades into fog; everything you explore is saved per world and dimension.
- Classic and vivid palettes for players who prefer Minecraft's own map colors.
- In single-player, `/remnantmap preload <radius>` fills in the map from the world save without walking
  there, and never generates terrain. See [docs/map-preload.md](docs/map-preload.md).

**Minimap**
- Round map in a gold ring, or a square chart frame.
- Smooth rotation and sub-pixel scrolling (map tiles are drawn as float-precision textured
  polygons, not re-composed per block), animated zoom, mipmapped at every zoom level.
- Player arrow, cardinal letters, coordinates, time of day and biome.
- Graces, beacons and bloodstains stay pinned to the rim when they are out of view.
- Optional radar for players, hostile and passive mobs.

**World map** (`M`)
- Full-screen map with drag-to-pan inertia, zoom toward the cursor, WASD panning.
- Left click places a golden beacon, right click opens the marker menu.
- Operators can teleport from the map (right click, **Teleport here**, then confirm) when the world or
  server allows it: cheats on in single-player, or the mod installed on the server. See
  [docs/map-teleport.md](docs/map-teleport.md).
- `G` opens the list of Sites of Grace and markers with search and category filters.
- Compass rose, day-cycle dial, scale bar, control hints and cloud-swirled corners.
- Far zoom levels switch to pre-built overview tiles so the whole explored world stays fast.

**Markers**
- 30 emblems (grace, castle, church, cave, catacombs, boss, treasure, waygate and more),
  tintable, with name, position and visibility options.
- Pillars of light in the world for beacons, graces and bloodstains, with distance labels.
  Distant pillars are pulled to the horizon so they stay visible.
- Automatic bloodstain on death and a resting-place marker at your last bed.
- Waypoints from Xaero's Minimap can be imported (Settings, Markers, **Import waypoints**), with a dry-run
  summary and a backup first. See [docs/xaero-import.md](docs/xaero-import.md).

## Controls

| Key | Action |
| --- | --- |
| `M` | Open / close the world map |
| `N` | Show / hide the minimap |
| `=` / `-` | Minimap zoom |
| `B` | Place a marker at your position |
| World map: `LMB` | Place or remove the beacon, select a marker |
| World map: `RMB` | Marker menu |
| World map: `G` | Grace and marker list |
| World map: `Space` | Return to your position |
| World map: `O` | Settings |
| World map: `H` | Toggle control hints |

All keys can be rebound in the controls menu. Settings are also available from the
NeoForge and Forge mod lists.

## Commands

| Command | Action |
| --- | --- |
| `/remnantmap preload <radius>` | Fill in the map within `<radius>` blocks from the save (single-player) |
| `/remnantmap preload status` | Show pre-load progress |
| `/remnantmap preload cancel` | Stop pre-loading |

## Data

- Settings: `config/remnantminimaps.json`
- Server settings (map teleport): `config/remnantminimaps-server.json` on the server, or in the game's
  own `config` folder for single-player.
- Map data and markers: `remnantminimaps/worlds/<world>/` in the game directory, one
  `.rmr` file per 256x256 block region.

## Building

Each Minecraft version is a separate MultiLoader Gradle workspace with its own wrapper (see the table above). The
root build drives all four:

```bash
./gradlew buildAll             # every workspace, tests included, then the 8 loader jars into releases/
./gradlew build262             # one workspace (also build1201, build1211, build263)
cd 1.20.1 && ./gradlew build   # Gradle 8.11 on Java 21 (Java 17 toolchain)
cd 1.21.1 && ./gradlew build   # Gradle 8.11 on Java 21
cd 26.3 && ./gradlew build     # Java 25 (the same for 26.2)
```

The root build hands the 26.x workspaces `JAVA_HOME_25_X64` or `JAVA_HOME_25` (or `-Pjava25Home=<jdk>`) and the
others `JAVA_HOME_21_X64` or `JAVA_HOME_21` (or `-Pjava21Home=<jdk>`); without them each workspace uses the current
`JAVA_HOME`. The 1.20.1 and 1.21.1 workspaces use Gradle 8.11, which cannot run on Java 25, so give them Java 21.
On Windows, run `.\gradlew.bat` instead of `./gradlew`.

Jars land in `<workspace>/<loader>/build/libs/remnantminimaps-<loader>-<minecraft>-<version>.jar`. Every
workspace's `gradle.properties` carries the same `version=`, and the root `VERSION` file matches it
(`./gradlew checkVersions`; set both with `./gradlew syncVersion -PmodVersion=x.y.z`).
`python3 scripts/check_targets.py workspaces` checks the targets, the shared version and the metadata ranges.

Run the game with `:fabric:runClient`, `:neoforge:runClient` or `:forge:runClient` inside a workspace; run folders
live in `<workspace>/<loader>/runs/`. `build` also runs the unit tests of the Minecraft-free `core/` code
(`:common:test`). The GUI art is generated by `tools/generate_assets.py` and the logo by `tools/generate_icon.py`
(Python 3 with Pillow and numpy).

## Releasing

CI (`.github/workflows/ci.yml`) builds all 8 jars on every push to `main` that touches the build. Publishing to
CurseForge is `.github/workflows/publish.yml`, run by hand (`version`, `dry_run`) or by a `v*.*.*` tag;
it never runs on a plain push. It refuses a second release of the mod on the same day (Africa/Tunis), takes the jars
from `releases/`, the GitHub Release or a build from source, and fails unless exactly the 8 jars are found. It needs
the secrets `CURSEFORGE_TOKEN` (upload token) and `CURSEFORGE_API_KEY` (Core API key) and the variable
`CURSEFORGE_ID` (the workflow falls back to this project's ID if the variable is unset). NightBeam publishes to
CurseForge only; the Modrinth page stays up but gets no new versions.

1. Bump the version with `./gradlew syncVersion -PmodVersion=X.Y.Z` (writes `VERSION` and every workspace).
2. Add a `# Remnants Minimap vX.Y.Z` section at the top of `CHANGELOG.md`; it becomes the release notes.
3. Prove it with a dry run (`gh workflow run publish.yml -f version=X.Y.Z -f dry_run=true`), then push a tag
   `vX.Y.Z` or run the **Publish** workflow by hand.

CurseForge has no API for the project description or logo, so paste `marketing/curseforge/description.md` there by
hand.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [.github/SECURITY.md](.github/SECURITY.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
