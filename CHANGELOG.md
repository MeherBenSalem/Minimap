# Odyssey Map v1.2.2

**Release Date:** August 27, 2026  
**Supported MC Versions:** 1.20.1 (Forge/Fabric), 1.21.1 (NeoForge/Fabric), 26.1.2 (NeoForge/Fabric), 26.2 (NeoForge/Fabric)

---

## Bug Fixes

- **Fixed MC 26.2 NeoForge/Fabric startup crash** — `MixinGui` targeted an obsolete `extractRenderState(GuiGraphicsExtractor, DeltaTracker)` descriptor. Vanilla 26.2 uses `extractRenderState(DeltaTracker, boolean, boolean)` and builds `GuiGraphicsExtractor` locally. The mixin now matches that signature, shadows `guiRenderState`, and draws the minimap before `applyCursor`.

### Upgrade Notes

1. Replace `odysseymap-*-26.2-1.2.1.jar` with the matching **1.2.2** loader jar.
2. No config reset required.

---

# Odyssey Map v1.2.1

**Release Date:** July 16, 2026  
**Supported MC Versions:** 1.20.1 (Forge/Fabric), 1.21.1 (NeoForge/Fabric), 26.1.2 (NeoForge/Fabric), 26.2 (NeoForge/Fabric)

---

## Bug Fixes

- **Fixed MC 26.2 client crash on launch (Fabric/NeoForge)** — `GameRendererAccessor` shadowed `mainCamera` as an interface field, which Java treats as `static`. Mixin rejected the apply with `STATIC modifier of @Shadow field mainCamera does not match the target`. Removed the broken accessor and now use the public `GameRenderer.mainCamera()` method.
- **Hardened LevelRenderer access on MC 26.2** — Replaced the same static `@Shadow` field pattern on `LevelRendererAccessor.submitNodeStorage` with a proper `@Accessor` getter so world waypoint rendering cannot hit the same mixin failure.

## Technical Changes

- Deleted `GameRendererAccessor` (Fabric + NeoForge) for MC 26.2.
- `MixinGameRenderer` now reads the camera via `mc.gameRenderer.mainCamera()`.
- `LevelRendererAccessor` uses `@Accessor("submitNodeStorage")` instead of an interface `@Shadow` field.

---

# Odyssey Map v1.2.0

**Release Date:** June 24, 2026  
**Supported MC Versions:** 1.20.1 (Forge/Fabric), 1.21.1 (NeoForge/Fabric), 26.1.2 (NeoForge/Fabric), 26.2 (NeoForge/Fabric)

---

## New Features

- **In-Game Settings Screen (MapSettingsScreen)** — A comprehensive configuration UI screen directly accessible inside Minecraft. Players no longer need to edit `config.json` manually in their mod folders.
  - Accessible via a new `Settings` button on the Fullscreen Map top bar.
  - Bound to the default `O` key (fully rebindable in standard Controls config).
  - Divided into 4 categorical tabs:
    - **Minimap**: Toggle minimap HUD, adjust size (64-512px), screen corner position, shape (circle/square), zoom, rotation mode, transparency, and show/hide options.
    - **HUD & Markers**: Toggle overlays (compass, coordinates) and configure individual marker visibility (players, waypoints, death, beds, portals, structures, entities, distance overlays, and clamping).
    - **Fullscreen Map**: Enable/disable map features, default zoom level, grid display, max waypoint limit, and safe render mode.
    - **Performance**: Configure performance preset presets, scan interval, columns per tick, border thickness, and custom border color (Hex ARGB field with a live color swatch).
  - **Auto-Saving**: Any changed options save automatically to the configuration file on click and apply in-game immediately without requiring client restarts.

## Technical Improvements

- Custom standalone screen implementation per version in the `common` subproject package `dev.nightbeam.odysseymap.gui` ensuring full compatibility across Fabric, Forge, and NeoForge mod loaders.
- Updated English locale keys in `en_us.json` for settings key mappings.

---

# Odyssey Map v1.1.0

**Release Date:** June 17, 2026  
**Supported MC Versions:** 1.20.1 (Forge/Fabric), 1.21.1 (NeoForge/Fabric), 26.1.2 (NeoForge/Fabric)

---

## Bug Fixes

- **Fixed fullscreen map crash (MC 26.1.2)** — Texture composition was running on the deferred GUI render thread, violating the new rendering pipeline. Moved `compose()` and `upload()` to the client tick thread via a new `FullscreenMapRenderer` coordinator class.
- **Fixed thread safety in TileCache** — Replaced `HashMap` with `ConcurrentHashMap` and made `currentDimension` volatile to prevent concurrent modification during map rendering.
- **Fixed texture upload/register crashes** — Wrapped `DynamicTexture.upload()` and `TextureManager.register()` in try-catch blocks with a `textureReady` flag to prevent hard crashes on GPU upload failures.
- **Fixed NullPointerException in player head rendering** — Added null checks for skin data (`getSkin().body().texturePath()` / `getSkin().texture()`) before blitting player head icons, with a fallback to a simple dot marker.
- **Fixed fullscreen map opening without player/level** — Added guards in `onOpenFullscreen()` to prevent opening the map when player or level is null.
- **Fixed minimum map dimensions** — Clamped fullscreen map panel to a minimum of 64x64 pixels to prevent degenerate rendering on small window sizes.

## Improvements

- **Fullscreen map UI refresh:**
  - Added a bordered frame around the map panel
  - Added a close button (X) in the top-right corner
  - Added zoom +/- buttons below the Recenter button
  - Added current zoom level display
  - Added current dimension label in the bottom-left corner
- **"Loading map..." fallback** — Shows a centered loading message when the map texture is not yet ready, instead of rendering nothing.
- **Safe render mode** — New config option `map.safeRenderMode` that throttles fullscreen map composition to every 3 ticks, skips the player head blit, and uses simple dot markers to reduce GPU load.
- **Configurable waypoint rendering limits** — New `map.maxWaypointsRendered` option (default 100) caps how many waypoints are drawn on the fullscreen map to prevent frame drops in waypoint-heavy worlds.
- **Null-safe fullscreen map opening** — The fullscreen map keybind now checks for player/level availability and config gates before opening.

## New Features

- **Waypoint search** — A search box in the fullscreen map sidebar filters waypoints by name in real time.
- **Waypoint favorites** — Click the star icon on any waypoint to favorite it. Favorites sort to the top of the list and are persisted to disk.
- **Waypoint visibility toggle** — Click the eye icon to hide/show individual waypoints without deleting them. Hidden waypoints are still saved but not rendered on the map.
- **New config options (under `map` section):**
  - `map.fullscreenEnabled` — Enable/disable the fullscreen map (default: true)
  - `map.showCoordinates` — Show coordinates overlay on fullscreen map (default: true)
  - `map.showWaypoints` — Show waypoints on fullscreen map (default: true)
  - `map.showPlayerMarker` — Show player marker on fullscreen map (default: true)
  - `map.maxWaypointsRendered` — Max waypoints rendered (default: 100)
  - `map.safeRenderMode` — Reduce rendering load (default: false)

## Technical Changes

- New `FullscreenMapRenderer` class handles deferred texture composition with dirty-flag caching and safe-mode throttling.
- `MinimapTexture` now exposes `isTextureReady()` for callers to check texture availability before blitting.
- `Marker` class gained `favorite` field with getter/setter.
- `MarkerManager` gained `toggleFavorite()` and `toggleVisibility()` methods.
- `MarkerStorage` now serializes/deserializes `visible` and `favorite` fields (backward compatible with old save files).
- `MarkerListWidget` rewritten with search filtering, favorite-first sorting, and clickable star/eye icons.
- Config values with `map.` prefix are serialized into a nested JSON object for cleaner config files.
- Updated `en_us.json` lang file with new translation keys.
