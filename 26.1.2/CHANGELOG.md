# Odyssey Map v1.1.0 Patch Notes

## Fixed
- Fixed a crash when opening the fullscreen map on Minecraft 26.1.2.
- Fixed render-thread violation: texture composition now runs on the client tick thread instead of the deferred GUI pipeline.
- Fixed `ConcurrentModificationException` race between tile scanning and texture composition by switching `TileCache` to `ConcurrentHashMap`.
- Fixed `NullPointerException` in player head rendering when skin data is not yet loaded.
- Added null safety for `client.player`, `client.level`, and `Minecraft.getInstance()` at all entry points.
- Added try/catch around texture upload and registration to prevent crashes from GPU errors.
- Added safe fallback ("Loading map...") when map tile data is not yet available.

## Improved
- Refreshed the fullscreen map UI with a cleaner layout:
  - 1px map frame border.
  - Close button (X) in top-right corner.
  - Zoom in (+) / zoom out (-) buttons with zoom level display.
  - Dimension name display (e.g. `minecraft:overworld`) in bottom-left.
  - Coordinates overlay gated by `mapShowCoordinates` config.
- Improved waypoint list with:
  - Search/filter box to find waypoints by name.
  - Favorite star toggle (gold star = favorited, sorted to top).
  - Visibility toggle (eye icon) to show/hide individual waypoints.
  - Favorites sorted first, then alphabetical.
  - Hidden waypoints shown in dimmed text.
- Improved map loading states — shows loading message instead of blank/crash.
- Reduced unnecessary work during fullscreen map rendering:
  - Texture composition skipped when pan/zoom/size unchanged.
  - Safe render mode throttles compose to every 3rd tick.
  - Waypoint rendering capped at configurable maximum.
  - Player head rendering uses dot marker in safe mode.
- Improved `MarkerStorage` to persist waypoint visibility and favorite state.

## Added
- Added `mapFullscreenEnabled` config — disable the fullscreen map entirely.
- Added `mapShowCoordinates` config — toggle coordinates overlay on fullscreen map.
- Added `mapShowWaypoints` config — toggle waypoints on fullscreen map.
- Added `mapShowPlayerMarker` config — toggle player marker on fullscreen map.
- Added `mapMaxWaypointsRendered` config — cap waypoints rendered on fullscreen map (default: 200).
- Added `mapSafeRenderMode` config — safe render mode for stability (default: enabled).
- Added search filter in waypoint sidebar.
- Added favorite/star waypoint support.
- Added per-waypoint visibility toggle.
