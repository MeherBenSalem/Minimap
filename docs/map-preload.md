# Pre-loading the map

`/remnantmap preload <radius>` fills in the map around you from the world save, without walking there.
It is a client command and works in **single-player** (and for the host of a LAN world).

```
/remnantmap preload 512      fill in everything within 512 blocks of you
/remnantmap preload status   show progress (also shown above the hotbar and on the world map)
/remnantmap preload cancel   stop; what is already drawn stays on the map
```

## What it does, and what it never does

- It reads chunks that **already exist in the save** of your single-player world and draws them with the
  same scanner as the live map, so pre-loaded land looks exactly like explored land.
- It **never generates terrain.** Chunks that were never generated are counted as "not generated yet" and
  stay in the fog. Chunks at the edge of the generated world that the game has not finished (no trees or
  surface yet) are skipped too.
- It **never loads chunks into the world.** No entities, block entities, redstone or farms are touched:
  the saved data is read through the game's own region storage (the same reader the game uses, so it is
  safe while the game runs) and decoded on a separate low-priority thread. Chunks saved by an older game
  version are upgraded in memory the same way the game would; the save itself is not changed.
- Chunks around you that the game already shows are skipped: the live map keeps those up to date, and the
  save may be a few minutes older than what you see.
- It works in the dimension you are in. In the Nether it maps the same height slice the live minimap
  shows (around your current height). Leaving the world or changing dimension stops it.

## Limits

| Setting (`config/remnantminimaps.json`) | Default | Range | Settings screen |
| --- | --- | --- | --- |
| `preloadMaxRadius` | `1024` blocks | 64 to 4096 | Performance, **Pre-load Radius** |
| `preloadChunksPerTick` | `8` | 1 to 32 | Performance, **Pre-load Speed** |

- A larger radius is cut down to `preloadMaxRadius` (with a message). The command itself accepts 16 to
  4096 blocks.
- At most `preloadChunksPerTick` saved chunks are requested per game tick, and at most four times that
  are in flight at once.
- Decoded chunks are drawn on the map within the existing **Mapping Budget** (`scanBudgetMs`, default
  2 ms per tick).
- New chunks are only requested while the map keeps up: when more than 6 map regions are waiting to be
  redrawn, or more than 256 decoded chunks are waiting to be drawn, requests pause.
- The work goes region by region (256 x 256 blocks), nearest first, so memory stays flat.

With the defaults that is up to 160 chunks per second; a radius of 1024 blocks (about 13,000 chunks) takes
a minute or two on a typical machine. The integrated server keeps running normally meanwhile.

## Why not on servers?

Map data lives on the client, and a server's world save is not on your computer. Doing this on a
dedicated server would need the server to read its save and send the map to the client; that is a larger
feature (the server would have to install the mod, and the data would have to be sent in small pieces). It
is not part of this version. On a server the map fills in as you explore.

## Why no option to generate new terrain?

Generating chunks is the expensive part of Minecraft: it can lag or stall a world for minutes and makes the
save permanently larger. Dedicated pre-generation mods do that job (with their own safeguards); this
command only shows land that already exists.
