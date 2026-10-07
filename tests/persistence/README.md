# Odyssey Map persistence regression tests

Run each workspace with its declared JDK (17, 21 or 25):

```sh
JAVA_HOME=/path/to/jdk-21 scripts/test_map_persistence.sh --version 1.21.1
```

Use `--version 1.20.1`, `1.21.1`, `26.1.2` or `26.2`. The script compiles the selected workspace's actual `Tile`, `TileStorage`, `TileCache`,
`WorldScanner`, `OdysseyMapClient`, `ClientEvents`, `MinimapTexture`, and
`FullscreenMapRenderer` sources. Narrow Minecraft/platform/graphics test doubles
provide deterministic world identity, chunk availability and texture pixels.
It does not launch Minecraft or replace an in-game integration test. No external
dependencies are required. Temporary output and test worlds are cleaned up. Each workspace also provides
`:common:persistenceSmokeTest`, which runs storage checks with actual Minecraft
resource keys and bytecode on the Gradle test classpath without test doubles.
CI runs both suites and full packaged-loader builds for all eight targets.

The unmodified 1.3.0 source baseline at `d4bf73e` reproduces three failures:

```sh
JAVA_HOME=/path/to/jdk-21 scripts/test_map_persistence.sh --baseline
```

That command deliberately exits nonzero after demonstrating explored-pixel loss
on unload/reopen, dimension round-trips, and scans of unloaded chunks.

## Storage behavior

- Singleplayer tiles live inside the save directory at `odysseymap/tiles-v1/`, so
  each save is isolated and map data follows the world if its folder is moved.
- Multiplayer tiles live in `config/odysseymap/maps/servers/<address-hash>/tiles-v1/`.
  Server addresses are trimmed and case-normalized; different ports remain
  separate. A server resetting its world at the same address is not automatically
  detectable by this client-only scheme.
- Dimension directories are SHA-256 hashes of their complete resource identifiers.
- Tile files contain a format version, dimension, coordinates, fixed pixel count
  and 128 x 128 32-bit pixels. Reads are bounded to one fixed-size tile. Writes
  replace files atomically using temporary files on the same filesystem.
- Current waypoint JSON, configuration and vanilla world files are unchanged.
  Previous releases stored no explored terrain, so there is no legacy terrain
  archive to migrate and previously discarded exploration cannot be recovered.
- Save before unload, logout, dimension/world switches and dirty eviction;
  additionally flush at most eight tiles once per second. Failed writes retain
  their original destination and data in memory for retry. Corrupt/newer files
  are preserved rather than overwritten. Transient read failures can recover
  existing pixels and merge newly sampled pixels without silent replacement.
- Cold render reads are queued, with at most eight tiles loaded per client tick.
  Normally at most 256 tile arrays are resident (about 16 MiB of pixel payload).
  Completed pixels remain in the composed texture after eviction. View changes
  cancel old requests and clear the old view. The cap is intentionally soft for
  unsaved exploration during disk failure; closing the process before storage
  recovers can still lose data that could not be written.

## Coverage

The suite covers full pixel round-trips, negative coordinates, dimension key
collisions, world/server separation, unloaded chunks, rendering versus storage
dirtiness, budgets, corrupt/future/truncated/oversized files, atomic replacement,
read/write/permission recovery, queued-read versus scan races, zero-valued
observations, changed views, progressive rendering beyond the resident limit,
edited evicted tiles, legacy files, and actual common lifecycle event orderings.
POSIX permission tests run on filesystems that support POSIX attributes.

Before publishing, perform complete loader builds and, when an actual game client is available, an in-game
NeoForge 1.21.1 run: explore several distant areas, save/quit, fully restart the
client, reopen, pan to the old areas outside loaded chunks, change dimensions,
and confirm waypoints and other worlds remain intact. Repeat relevant rendering
checks on each applicable loader and modern deferred-rendering version. If an actual game client is unavailable, disclose that remaining visual/gameplay validation gap; storage smoke tests are not a gameplay session.
