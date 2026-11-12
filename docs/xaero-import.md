# Importing waypoints from Xaero's Minimap

Remnants Minimap can copy the waypoints that **Xaero's Minimap** saved on your computer into the world
you are playing. Xaero's mod does not need to be installed; nothing of it is used.

## How to

1. Join the world (single-player or server) you want the waypoints in.
2. Open the settings (`O` on the world map, or the key you bound), go to **Markers** and press
   **Import waypoints**.
3. The screen picks the Xaero folder that matches this world, if it finds one. Use `<` and `>` to switch
   between the folders it found, or type the path of a Xaero world folder.
4. Read the summary: how many waypoints were found, how many are new, how many are already on your map,
   and what was skipped. Nothing has changed at this point.
5. Press **Import N waypoints**. `markers.json` of this world is copied to
   `markers.json.bak-<date>-<time>` first (in `remnantminimaps/worlds/<world>/`), then the new markers are
   added. Existing markers are never changed or removed. To undo, quit the world and copy the backup back
   over `markers.json`.

## What is imported

| Xaero's Minimap | Remnants Minimap |
| --- | --- |
| Waypoint name (or initials when the name is empty) | Marker name (cut to 64 characters, formatting codes removed) |
| x, y, z (y may be unknown, `~`) | Marker position (without height when unknown) |
| Color 0 to 15 (Minecraft's text colors) | Marker tint |
| Disabled | Hidden marker |
| Deathpoint / old deathpoint | Marker with the Bloodstain emblem, named "Deathpoint" / "Old deathpoint" |
| Dimension folder `dim%0`, `dim%-1`, `dim%1` | Overworld, Nether, End |
| Dimension folder `dim%namespace$path` | Dimension `namespace:path` (modded dimensions) |

A waypoint is "already on your map" when a marker with the same name exists at the same x and z in the
same dimension; it is skipped. Waypoint sets, initials, teleport options and visibility settings have no
equivalent and are ignored.

**Not imported:** Xaero's World Map tiles (explored areas), Xaero's settings, and waypoints of dimension
folders that cannot be mapped to a dimension id (they are counted as skipped).

## Supported versions and limits

- Folders: `xaero/minimap/<world>/` (Xaero's Minimap 24.3 and later) and the older
  `XaeroWaypoints/<world>/`, both in the game directory, or any folder you type in.
- Files: every `.txt` file in a world's `dim%...` folders (`mw$default_1.txt` and other multiworld files,
  and the very old `waypoints.txt`).
- Line formats: `waypoint:name:initials:x:y:z:color:disabled:type:set:...` with 9 or more fields. This
  covers the current format (14 fields, with visibility and destination) and older ones (12 fields with
  `global`, or fewer). Later fields are ignored, so new additions do not break the import.
- Safety limits: files over 4 MB are not read, at most 10,000 waypoints per file and 5,000 markers per
  import; symbolic links are not followed; damaged lines are counted and skipped.

## Feasibility and licensing

Checked before implementing (November 2026):

- **License.** Xaero's Minimap and Xaero's World Map are "All Rights Reserved"; their pages grant
  permissions for modpacks and videos and say nothing about other mods reading the files they write. This
  import does not copy, bundle, decompile or depend on any of Xaero's code or assets, and it does not
  redistribute their data: it only reads text files that the player's own game wrote to the player's own
  computer, when the player asks for it.
- **Waypoint format.** Plain text, one waypoint per line. The format is written into every file's own
  header comment (`#waypoint:name:initials:x:y:z:color:disabled:type:set:...`) and is described by
  several independent open-source converters, for example
  [MC_Waypoint_Converter](https://github.com/brothanb/MC_Waypoint_Converter) (Unlicense) and a
  [Meteor/Xaero waypoint script](https://gist.github.com/MinekPo1/d929f0ed1a3436054c2d62c33cf987a1); a
  [server wiki](https://minecraftonline.com/wiki/Minimap_Waypoints) shows real example lines. The folder
  move from `XaeroWaypoints` to `xaero/minimap` in version 24.3.0 is in Xaero's own
  [changelog](https://chocolateminecraft.com/update.php?mod_id=0). Implemented from those descriptions.
- **World map format: not supported.** Xaero's World Map stores explored areas in a binary format inside
  zip files (`region.xaero`). There is no public description of it; reading it would mean reverse
  engineering Xaero's code or guessing from samples, which could not be done reliably or safely here.
  The explored map is therefore not imported; Remnants Minimap draws it again as you explore (or, in
  single-player, with `/remnantmap preload` where available).
- The tests use small synthetic files written by the tests themselves; no real Xaero files are part of
  this repository.
