# Odyssey Map (26.1.2)

Client-side minimap mod for Minecraft **26.1.2** with Fabric and NeoForge loaders.

## Requirements

- JDK **25**
- Minecraft **26.1.2**

## Build

```bash
./gradlew :fabric:build :neoforge:build
```

Artifacts:

- `fabric/build/libs/odysseymap-fabric-26.1.2-*.jar`
- `neoforge/build/libs/odysseymap-neoforge-26.1.2-*.jar`

## Run client

```bash
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

## IDE

Open this folder (`26.1.2/`) as the Gradle root project and set the project SDK to Java 25.

If Gradle fails with `Unsupported class file major version`, your default Java is too new (e.g. Java 26). Point `JAVA_HOME` at JDK 25 before running Gradle.

## Controls

| Key | Action |
|-----|--------|
| M | Toggle minimap |
| J | Fullscreen map |
| = / - | Zoom in / out |
| B | Create waypoint |
