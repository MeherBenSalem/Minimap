# Odyssey Map

**Explore. Navigate. Never get lost again.** A clean, lightweight minimap and fullscreen map for Minecraft Forge.

---

## A Simple Map That Gets Out of Your Way

Odyssey Map is a **client-side minimap mod** built for players who want a clear overview of the world without clutter, performance hits, or confusing menus. Drop it into any modpack — it just works. Toggle the HUD, set a waypoint, open the fullscreen map, and keep adventuring.

Designed to feel like a natural extension of Minecraft, Odyssey Map blends a smooth UI with accurate terrain rendering straight from your loaded chunks.

---

## ✨ Features

- **🗺️ Clean circular or square minimap HUD** — choose the style that fits your screen
- **🧭 Heading-up or north-up rotation** — your preference, switchable any time
- **⛰️ Real-time terrain rendering** — colors pulled from your actual loaded chunks (multiplayer-safe)
- **📍 Waypoints, death points, beds, and portals** — automatic + custom markers
- **🖥️ Fullscreen map screen** — pan, zoom, and manage all your markers in one place
- **🎨 Player head icon** at your position on the map
- **🧱 Block color overrides** — fine-tune the map palette to your taste
- **⚙️ Fully configurable** — a simple Forge config file controls every option
- **🚀 Lightweight by design** — built for modpacks and big worlds

---

## 🧭 Why Odyssey Map?

Most minimap mods are either too bloated, too plain, or too aggressive with overlays. Odyssey Map aims for the middle ground:

- **Readable over flashy** — no neon markers, no fancy 3D rotation, no screen hogging
- **Honest visuals** — terrain is rendered from real chunk data, not faked
- **Modpack friendly** — works on vanilla servers, modded servers, and packed servers alike
- **No telemetry, no nonsense** — pure client-side, no data sent anywhere

If you just want a map that looks good and tells you where you are, this is it.

---

## 🖥️ Compatibility

- **Minecraft:** 1.20.1
- **Mod Loader:** Minecraft Forge (47.2.0+)
- **Side:** Client-only (no server install required)
- **Multiplayer:** Works on any server the client can connect to
- **Other Mods:** Compatible with most minimap-adjacent mods; we don't read or modify other HUDs

> ✅ No Fabric or NeoForge version is planned at this time. Forge 1.20.1 only.

---

## 📦 Installation

1. Install **Minecraft Forge 1.20.1** (47.2.0 or newer) if you haven't already.
2. Download the latest `odysseymap-1.0.0.jar` from the **Files** tab above.
3. Drop the jar into your `.minecraft/mods` folder.
4. Launch Minecraft and enjoy the map!

**For modpack authors:** Odyssey Map is client-side only, so players can enable/disable it independently of the server. No server-side install is required.

---

## ⌨️ Default Keybindings

| Key | Action |
|-----|--------|
| `M` | Toggle minimap HUD |
| `J` | Open fullscreen map |
| `=` / `-` | Zoom in / out |
| `B` | Create waypoint at current position |

All keybindings are remappable through Minecraft's **Options → Controls** menu.

---

## ⚙️ Configuration

Odyssey Map generates two config files in `.minecraft/config/`:

- `odysseymap-client.toml` — main settings (size, position, shape, rotation, transparency, markers, zoom, etc.)
- `odysseymap-blocks.toml` — per-block color overrides (e.g. `minecraft:stone=minecraft:grass_block`)

Every option has a comment explaining what it does. Most players won't need to touch the configs at all.

---

## 🛠️ Known Limitations

- **Performance** depends on how many chunks are loaded — very dense modpacks with aggressive chunk loading may need a higher `zoomBlocksPerPixel` setting
- **Underground and unloaded chunks** are blank by design (we only render what you can actually see)
- **Cave lighting** is not rendered — the map shows the surface only
- **No minimap for Nether/End portals** in the HUD (they appear as markers only)

---

## ❓ FAQ

**Does this work on servers?**
Yes. Odyssey Map is client-side, so it works on any server your client can connect to. The server doesn't need the mod installed.

**Will it slow down my game?**
No noticeable impact on modern hardware. The mod samples terrain once per chunk and caches the results.

**Can I use it in my modpack?**
Absolutely — that's exactly what it's built for. Go ahead.

**Does it have a cave mode?**
Not currently. It's surface-focused by design.

**Can I move the minimap to a different corner?**
Yes — corner position is configurable.

**Does it work with shaders?**
Yes, the HUD renders through Minecraft's standard overlay system.

---

## 💬 Support

- **Issues & Bug Reports:** Use the issue tracker on the project page
- **Questions & Discussion:** Comments section below or community Discord
- **Source Code:** Available on the project page

I'll do my best to respond to bug reports, but this is a hobby project — please be patient.

---

## 📜 Changelog

### 1.0.0 — Initial Release
- First public release of Odyssey Map for Forge 1.20.1
- Minimap HUD with circular/square shape and configurable size
- Heading-up and north-up rotation modes
- Waypoints, death, bed, and portal markers
- Fullscreen map screen with pan and zoom
- Player head icon rendering
- Block color override config
- Performance options for low-end hardware

---

## 📄 License & Permissions

Odyssey Map is released under the **GPL-3.0** license.

You are free to:
- ✅ Use it in any modpack (public or private)
- ✅ Modify it for personal use
- ✅ Redistribute the original jar

You may **not**:
- ❌ Re-upload and claim it as your own work
- ❌ Sell the mod or repackaged versions

Source code is available in the jar. See `LICENSE` for full terms.

---

## 🙏 Credits

- **Vanilla Minimaps** (JNNGL) — original column sampling and map color logic that inspired this port
- **Godlander** — additional map color improvements

Odyssey Map is an independent Forge port, written from scratch for the client-side HUD experience.

---

⭐ If you enjoy the mod, leaving a star and a kind comment on CurseForge really helps — thanks for trying Odyssey Map!
