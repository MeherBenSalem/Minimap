# Odyssey Map v1.2.0 Patch Notes

## Summary
This update introduces a fully integrated, vanilla-styled **In-Game Settings Screen** to Odyssey Map. Players can now configure all HUD, Minimap, Fullscreen Map, and Performance settings directly from within Minecraft without editing the configuration files manually.

---

## What's New

### 1. In-Game Settings Screen (`MapSettingsScreen`)
We've added a clean UI accessible in-game to manage all configurable options.
- **Minimap HUD Options**: Toggle minimap HUD, adjust size (64 to 512 pixels), screen position corner, shape (circle vs square), zoom blocks per pixel, rotation mode, HUD transparency, and hide-in-menus options.
- **Markers & HUD**: Manage overlays (compass, coordinates, player heads) and toggle individual marker types (players, waypoints, death points, beds, portals, structures, entities, marker distance overlay, and border clamping).
- **Fullscreen Map**: Toggle fullscreen map, coordinate overlays, waypoint icons, player pointers, default zoom level, grid overlays, waypoint render limits, and safe rendering mode.
- **Performance & Borders**: Cycle performance level presets, columns sampled per tick, tick scan interval, border thickness, and customize border ARGB color via a text field with a live color swatch preview.

### 2. Auto-Saving & Instant Updates
- Any options modified in-game are automatically saved to `config.json` immediately.
- Changed settings are applied in real time (e.g. changing zoom resets zoom states and invalidates map tile caches dynamically).

### 3. Dedicated Access Options
- Added a **Settings** button in the top bar of the Fullscreen Map (`FullscreenMapScreen`).
- Added a new bindable keyboard hotkey (`O` by default, listed under the "Odyssey Map" category in standard Controls menu) to open settings directly.

---

## Technical Details & Mod Versions
- **MC 1.20.1**: Mod version updated to **1.2.0** (Fabric + Forge)
- **MC 1.21.1**: Mod version updated to **1.2.0** (Fabric + NeoForge)
- **MC 26.1.2**: Mod version updated to **1.2.0** (Fabric + NeoForge)
- **MC 26.2**: Mod version updated to **1.2.0** (Fabric + NeoForge)
- standalone native implementation using the native GUI rendering stack of each Minecraft version (resolves package differences between `GuiGraphics`, `GuiGraphicsExtractor`, and `MouseButtonEvent` lifecycle changes).
