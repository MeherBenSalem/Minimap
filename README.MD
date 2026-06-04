# Odyssey Map

Client-side minimap mod for **Minecraft Forge 1.20.1**.

Converted from the Vanilla Minimaps server plugin concept into a native Forge mod with real HUD rendering, client-side chunk sampling, markers, and a fullscreen map.

## Features

- Circular or square minimap HUD overlay
- Smooth heading-up / north-up rotation
- Real-time terrain from loaded client chunks (multiplayer-safe)
- Improved colors with biome tinting and block overrides
- Markers: players, waypoints, death, beds, portals, entities
- Fullscreen map with pan, zoom, and waypoint management
- Forge client config (`odysseymap-client.toml`, `odysseymap-blocks.toml`)

## Keybinds (default)

| Key | Action |
|-----|--------|
| M | Toggle minimap |
| J | Open fullscreen map |
| = / - | Zoom in / out |
| B | Create waypoint at player position |

## Build

```bash
./gradlew build
```

Output: `build/libs/odysseymap-1.0.0.jar`

## Run client

```bash
./gradlew runClient
```

## Credits

- Original **Vanilla Minimaps** plugin by JNNGL (column sampling / map color logic)
- Godlander map color improvements (referenced in original plugin)

## License

GPL-3.0-only (see LICENSE)
