# Teleporting from the world map

Operators can teleport by clicking the world map. The client only asks; the server decides, checks
everything again and moves the player. Remnants Minimap stays a client mod: this is an optional
server half that runs in single-player (the built-in server) and on servers that install the mod too.
On a server without the mod, the action never appears.

## Using it

1. Open the world map (`M`).
2. Right-click an explored spot and choose **Teleport here**, or right-click a marker (on the map or in
   the `G` list) and choose **Teleport to this marker**.
3. A second menu asks **Confirm: teleport to X, Z**. Only that click sends the request, so a stray click
   never moves you. The map closes and you arrive a moment later, once the area has loaded.

The actions only show when the server allows you to teleport. The bottom hint then reads
`RMB Markers & teleport` instead of `RMB Marker menu`. Unexplored (foggy) land cannot be picked from the
map, because the map does not know what is there.

## Who may teleport

| Situation | Result |
| --- | --- |
| Single-player or LAN, cheats off | Refused ("needs cheats"), whatever the config says |
| Single-player or LAN, cheats on | Allowed (the host has operator level 4) |
| Server with the mod, player below the configured level | Refused |
| Server with the mod, player at or above the configured level (default 2, like `/tp`) | Allowed |
| Server without the mod | Not offered |

The level check is a permission node where the loader has a permission API, so permission mods
(LuckPerms and similar) can grant or deny it per player or group:

| Loader | Permission node | Default when no permission mod decides |
| --- | --- | --- |
| NeoForge (26.x, 1.21.1) | `remnantminimaps.map_teleport` (NeoForge `PermissionAPI`) | configured operator level |
| Forge 1.20.1 | `remnantminimaps.map_teleport` (Forge `PermissionAPI`) | configured operator level |
| Fabric 26.3, 26.2 | `remnantminimaps:map_teleport` (Fabric API permission module) | configured operator level |
| Fabric 26.1.2, 1.21.1, 1.20.1 | none (that Fabric API has no permission module) | configured operator level |

The single-player rule ("cheats on") is checked before the node, so a permission mod cannot open map
teleport in a world without cheats.

Other mods can refuse a teleport with `io.nightbeam.remnantminimaps.api.MapTeleportGuard`, for example
for claimed land. Guards run on the server thread with the final landing position:

```java
MapTeleportGuard.register((player, level, landing) ->
        Claims.isForeign(player, landing) ? Component.literal("This land is claimed") : null);
```

No other mod is required.

## Where you land

The server loads the target chunk first (with a temporary ticket, like a nether portal does) and then
looks at the real blocks:

- **Open-sky dimensions (Overworld, End):** you land on top of the highest block of the column: the
  ground, a tree canopy, a roof, or the surface of water. It never digs down into caves.
- **Dimensions with a ceiling (Nether):** it searches the column from the height the map shows
  downwards, then upwards, and never goes above the dimension's ceiling (no landing on the bedrock roof).
- **Safe means:** the block below is solid (or water), and the feet and head blocks are free of anything
  solid or harmful.
- **Never lands on or in:** lava, fire, soul fire, campfires, magma, cactus, sweet berry bushes, wither
  roses, powder snow, cobwebs, pointed dripstone, lava cauldrons or portals. A column of nothing but air
  (the void) has no landing.
- If the clicked column is not safe, the closest safe column within `landingSearchRadius` blocks is used.
  If there is none, the teleport is refused with "No safe place to land there."
- The target must be inside the world border, and in the dimension the map shows (the one you are in).
  A teleport never changes dimension.
- By default the target chunk must already exist in the save (`allowUngeneratedChunks: false`), so map
  clicks do not create new land. Only the target chunk is loaded to full status; as whenever the game loads
  a chunk, the chunks right around it are prepared in the background.
- Landing columns in neighbouring chunks are only used when those chunks are already fully loaded.

## Logging

Every teleport is logged like a command: the server log gets
`<name> teleported from the world map to x y z in <dimension>`, and the vanilla
`Teleported <name> to x, y, z` feedback goes to the player and, as with `/tp`, to other operators and the
console when the `sendCommandFeedback` and `logAdminCommands` game rules allow it. Refusals are logged with
their reason.

## Server configuration

`config/remnantminimaps-server.json` on the server. In single-player it is in the game's own `config`
folder. The file is written with the defaults on the first start and read every time the server (or
single-player world) starts; a broken file is left untouched and the defaults are used.

```json
{
  "mapTeleport": {
    "enabled": true,
    "permissionLevel": 2,
    "cooldownSeconds": 3,
    "allowUngeneratedChunks": false,
    "landingSearchRadius": 3
  }
}
```

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `mapTeleport.enabled` | `true` | | Master switch. `false` turns map teleport off for everyone. |
| `mapTeleport.permissionLevel` | `2` | 0 to 4 | Operator level needed (2 is what `/tp` needs). 0 lets every player teleport on a dedicated server. |
| `mapTeleport.cooldownSeconds` | `3` | 0 to 3600 | Wait between two map teleports of the same player. |
| `mapTeleport.allowUngeneratedChunks` | `false` | | Allow targets in chunks that were never generated. Loading them generates terrain. |
| `mapTeleport.landingSearchRadius` | `3` | 0 to 8 | How far around the clicked block to look for a safe landing. |

Map teleport is on by default for operators because they can already use `/tp`; it does not give anyone
a power they did not have. To turn it off, set `enabled` to `false` and restart the server.

## Network

Two tiny messages, only exchanged when both sides have the mod: `remnantminimaps:map_teleport`
(client to server: dimension, x, z and a height hint) and `remnantminimaps:map_teleport_access`
(server to client: whether the player may teleport, sent when it changes). The channels are optional,
so vanilla clients can join a server with the mod and modded clients can join vanilla servers.
