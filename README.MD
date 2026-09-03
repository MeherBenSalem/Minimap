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

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [.github/SECURITY.md](.github/SECURITY.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
