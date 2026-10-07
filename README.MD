# Odyssey Map

Client-side Minecraft minimap with real-time terrain rendering, markers, waypoints,
and a fullscreen map. Formerly known as MiniMaps / Vanilla Minimaps.

## Supported versions

| Version root | Minecraft | Loaders | Java |
| --- | --- | --- | --- |
| `1.20.1/` | 1.20.1 | Fabric, Forge | 17 |
| `1.21.1/` | 1.21.1 | Fabric, NeoForge | 21 |
| `26.1.2/` | 26.1.2 | Fabric, NeoForge | 25 |
| `26.2/` | 26.2 | Fabric, NeoForge | 25 |

Each version root is an independent Gradle project.

## Features

- Real-time terrain minimap with smooth rotation
- Waypoints and markers
- Fullscreen map view
- In-game settings screen

## Building

```powershell
$env:JAVA_HOME_17 = "C:\Program Files\Eclipse Adoptium\jdk-17*"
$env:JAVA_HOME_21 = "C:\Program Files\Eclipse Adoptium\jdk-21*"
$env:JAVA_HOME_25 = "C:\Program Files\Eclipse Adoptium\jdk-25*"
$env:JAVA_HOME = $env:JAVA_HOME_21
.\gradlew.bat buildAll --no-daemon
```

Built mod JARs are collected under `all-jars/`.

## Explored terrain persistence

Starting with 1.3.1, explored terrain is saved separately for each singleplayer
save, configured multiplayer server address and dimension. Singleplayer map data
lives inside the save folder under `odysseymap/tiles-v1/`; multiplayer map data
lives under `config/odysseymap/maps/servers/`. Existing waypoint/config files are
unchanged. Previously discarded exploration cannot be reconstructed.

Large saved views load progressively with at most 256 normal resident terrain
tiles and a small per-tick read budget. Unsaved tiles are retained beyond that
soft limit when storage fails; exiting before writes recover can lose those
unsaved changes. A server resetting its world at the same address is not
automatically distinguishable by this client-only scheme.

## Verification

GitHub CI builds each supported loader/version independently, runs deterministic
persistence/rendering regressions and real-Minecraft storage smoke checks, and
inspects the packaged JAR metadata/classes. See
[the persistence test guide](tests/persistence/README.md) for local checks and the
in-game restart/dimension/pan validation checklist. Unit and storage smoke tests
do not claim full in-game visual validation.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [.github/SECURITY.md](.github/SECURITY.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
