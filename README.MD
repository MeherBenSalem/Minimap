# MiniMaps (Odyssey Map)

This repository contains self-contained MultiLoader projects per Minecraft version:

| Folder | Minecraft | Loaders | JDK | Status |
|--------|-----------|---------|-----|--------|
| [`1.20.1/`](1.20.1/) | 1.20.1 | Fabric + Forge | 17 | Archived |
| [`1.21.1/`](1.21.1/) | 1.21.1 | Fabric + NeoForge | 21 | Maintained |
| [`26.1.2/`](26.1.2/) | 26.1.2 | Fabric + NeoForge | 25 | Active |

## Building 26.1.2 (active)

Requires **JDK 25** (Gradle uses the Java 25 toolchain; set `JAVA_HOME` if your default JRE is newer).

```bash
cd 26.1.2
./gradlew :fabric:build :neoforge:build
```

Run the client (from `26.1.2/`):

```bash
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

Open `26.1.2/` as the Gradle project in your IDE with JDK 25.

## Building 1.21.1

```bash
cd 1.21.1
./gradlew :fabric:build :neoforge:build
```

Open `1.21.1/` as the Gradle project in IntelliJ with JDK 21.
