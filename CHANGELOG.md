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
