package io.nightbeam.remnantminimaps.client.session;

import io.nightbeam.remnantminimaps.RemnantMinimaps;
import io.nightbeam.remnantminimaps.client.render.gfx.GuiTexture;
import io.nightbeam.remnantminimaps.client.scan.ChunkScanner;
import io.nightbeam.remnantminimaps.core.io.RegionFile;
import io.nightbeam.remnantminimaps.core.map.Columns;
import io.nightbeam.remnantminimaps.core.map.RegionData;
import io.nightbeam.remnantminimaps.core.style.MapStyle;
import io.nightbeam.remnantminimaps.core.style.Stylizer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Everything known about one dimension of one world: loaded regions, their GPU textures, the
 * zoomed-out overview tiles, and the disk files behind them. Client thread only, except for the
 * jobs it hands to the IO executor, which only ever touch private copies.
 */
public final class DimensionMap {
    private static final long AUTOSAVE_MS = 30_000L;
    private static final long EVICT_IDLE_MS = 5_000L;
    private static final long OVERVIEW_IDLE_MS = 120_000L;
    private static final int RESTYLE_ROWS = 32;

    public final String dimensionId;
    private final Path dir;
    private final Executor io;

    private final Long2ObjectOpenHashMap<RegionData> regions = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<GuiTexture> textures = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<OverviewTile> overview = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<ChunkScanner.Result>> pending = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet onDisk = new LongOpenHashSet();
    private final LongOpenHashSet loading = new LongOpenHashSet();
    private final LongOpenHashSet thumbRequested = new LongOpenHashSet();
    private final ConcurrentLinkedQueue<Runnable> completions = new ConcurrentLinkedQueue<>();

    private MapStyle style;
    private int stamp;
    private int maxLoaded;
    private boolean indexReady;
    private long lastAutosave = System.currentTimeMillis();
    private int revision;

    private RegionData lookupCache;

    private final Stylizer.Neighbors neighbors = new Stylizer.Neighbors() {
        @Override
        public int color(int wx, int wz) {
            RegionData r = regionAt(wx, wz);
            return r == null ? 0 : r.colors[RegionData.index(wx & RegionData.MASK, wz & RegionData.MASK)];
        }

        @Override
        public int height(int wx, int wz) {
            RegionData r = regionAt(wx, wz);
            return r == null ? 0 : r.heights[RegionData.index(wx & RegionData.MASK, wz & RegionData.MASK)];
        }

        @Override
        public int depth(int wx, int wz) {
            RegionData r = regionAt(wx, wz);
            return r == null ? 0 : r.depths[RegionData.index(wx & RegionData.MASK, wz & RegionData.MASK)];
        }
    };

    public DimensionMap(String dimensionId, Path dir, Executor io, MapStyle style, int maxLoaded) {
        this.dimensionId = dimensionId;
        this.dir = dir;
        this.io = io;
        this.style = style;
        this.stamp = style.stamp();
        this.maxLoaded = maxLoaded;
        io.execute(() -> {
            LongOpenHashSet found = listRegionFiles(dir);
            completions.add(() -> {
                onDisk.addAll(found);
                indexReady = true;
                drainPendingAfterIndex();
            });
        });
    }

    private static LongOpenHashSet listRegionFiles(Path dir) {
        LongOpenHashSet found = new LongOpenHashSet();
        if (!Files.isDirectory(dir)) {
            return found;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(path -> {
                String name = path.getFileName().toString();
                if (name.startsWith("r.") && name.endsWith(".rmr")) {
                    String[] parts = name.split("\\.");
                    if (parts.length == 4) {
                        try {
                            found.add(RegionData.key(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            });
        } catch (IOException e) {
            RemnantMinimaps.LOG.warn("Could not list map regions in {}", dir, e);
        }
        return found;
    }

    private Path regionPath(int rx, int rz) {
        return dir.resolve(RegionFile.fileName(rx, rz));
    }

    /** Bumped whenever region contents change; lets screens know something new is visible. */
    public int revision() {
        return revision;
    }

    public RegionData region(int rx, int rz) {
        return regions.get(RegionData.key(rx, rz));
    }

    private RegionData regionAt(int wx, int wz) {
        int rx = wx >> RegionData.SHIFT;
        int rz = wz >> RegionData.SHIFT;
        RegionData cached = lookupCache;
        if (cached != null && cached.rx == rx && cached.rz == rz) {
            return cached;
        }
        RegionData r = regions.get(RegionData.key(rx, rz));
        if (r != null) {
            lookupCache = r;
        }
        return r;
    }

    /** Packed column at a world position, or 0 when unknown or not loaded. */
    public int columnColor(int wx, int wz) {
        return neighbors.color(wx, wz);
    }

    public int columnHeight(int wx, int wz) {
        return neighbors.height(wx, wz);
    }

    public boolean isKnown(int rx, int rz) {
        long key = RegionData.key(rx, rz);
        return regions.containsKey(key) || onDisk.contains(key);
    }

    // ------------------------------------------------------------------ scanning

    public void applyScan(ChunkScanner.Result scan) {
        int rx = scan.chunkX >> 4;
        int rz = scan.chunkZ >> 4;
        long key = RegionData.key(rx, rz);
        RegionData region = regions.get(key);
        if (region == null) {
            if (!indexReady || loading.contains(key) || onDisk.contains(key)) {
                pending.computeIfAbsent(key, k -> new ArrayList<>()).add(copy(scan));
                if (indexReady) {
                    requestLoad(rx, rz);
                }
                return;
            }
            region = createRegion(rx, rz);
        }
        write(region, scan);
    }

    private RegionData createRegion(int rx, int rz) {
        RegionData region = new RegionData(rx, rz);
        region.styleStamp = stamp;
        region.lastTouched = System.currentTimeMillis();
        regions.put(region.key(), region);
        return region;
    }

    private static ChunkScanner.Result copy(ChunkScanner.Result scan) {
        ChunkScanner.Result c = new ChunkScanner.Result();
        System.arraycopy(scan.colors, 0, c.colors, 0, 256);
        System.arraycopy(scan.heights, 0, c.heights, 0, 256);
        System.arraycopy(scan.depths, 0, c.depths, 0, 256);
        c.chunkX = scan.chunkX;
        c.chunkZ = scan.chunkZ;
        return c;
    }

    private void write(RegionData region, ChunkScanner.Result scan) {
        int lx0 = (scan.chunkX & 15) << 4;
        int lz0 = (scan.chunkZ & 15) << 4;
        if (!region.writeChunk(lx0, lz0, scan.colors, scan.heights, scan.depths)) {
            return;
        }
        revision++;
        // Styling looks two blocks into the neighbors, so their facing edges may change too.
        boolean west = lx0 == 0;
        boolean east = lx0 == RegionData.SIZE - 16;
        boolean north = lz0 == 0;
        boolean south = lz0 == RegionData.SIZE - 16;
        if (west) markNeighbor(region.rx - 1, region.rz, RegionData.SIZE - 2, lz0 - 2, RegionData.MASK, lz0 + 17);
        if (east) markNeighbor(region.rx + 1, region.rz, 0, lz0 - 2, 1, lz0 + 17);
        if (north) markNeighbor(region.rx, region.rz - 1, lx0 - 2, RegionData.SIZE - 2, lx0 + 17, RegionData.MASK);
        if (south) markNeighbor(region.rx, region.rz + 1, lx0 - 2, 0, lx0 + 17, 1);
        if (west && north) markNeighbor(region.rx - 1, region.rz - 1, RegionData.SIZE - 2, RegionData.SIZE - 2, RegionData.MASK, RegionData.MASK);
        if (east && north) markNeighbor(region.rx + 1, region.rz - 1, 0, RegionData.SIZE - 2, 1, RegionData.MASK);
        if (west && south) markNeighbor(region.rx - 1, region.rz + 1, RegionData.SIZE - 2, 0, RegionData.MASK, 1);
        if (east && south) markNeighbor(region.rx + 1, region.rz + 1, 0, 0, 1, 1);
    }

    private void markNeighbor(int rx, int rz, int x0, int z0, int x1, int z1) {
        RegionData neighbor = regions.get(RegionData.key(rx, rz));
        if (neighbor != null) {
            neighbor.markStyleDirty(x0, z0, x1, z1);
        }
    }

    private void drainPendingAfterIndex() {
        long[] keys = pending.keySet().toLongArray();
        for (long key : keys) {
            int rx = RegionData.keyX(key);
            int rz = RegionData.keyZ(key);
            if (onDisk.contains(key)) {
                requestLoad(rx, rz);
            } else {
                RegionData region = createRegion(rx, rz);
                List<ChunkScanner.Result> scans = pending.remove(key);
                for (ChunkScanner.Result scan : scans) {
                    write(region, scan);
                }
            }
        }
    }

    // ------------------------------------------------------------------ loading

    public void requestLoad(int rx, int rz) {
        long key = RegionData.key(rx, rz);
        if (!indexReady || !onDisk.contains(key) || loading.contains(key) || regions.containsKey(key)) {
            return;
        }
        loading.add(key);
        MapStyle loadStyle = style;
        Path path = regionPath(rx, rz);
        io.execute(() -> {
            RegionData loaded = null;
            try {
                RegionFile.Snapshot s = RegionFile.read(path);
                loaded = new RegionData(rx, rz, s.colors(), s.heights(), s.depths());
                Stylizer.stylizeAll(loadStyle, loaded, Stylizer.EMPTY);
            } catch (IOException | RuntimeException e) {
                RemnantMinimaps.LOG.warn("Could not read map region {}", path, e);
            }
            RegionData result = loaded;
            completions.add(() -> install(rx, rz, result, loadStyle.stamp()));
        });
    }

    private void install(int rx, int rz, RegionData loaded, int loadedStamp) {
        long key = RegionData.key(rx, rz);
        loading.remove(key);
        if (regions.containsKey(key)) {
            return;
        }
        RegionData region;
        if (loaded == null) {
            onDisk.remove(key);
            region = createRegion(rx, rz);
        } else {
            region = loaded;
            region.lastTouched = System.currentTimeMillis();
            regions.put(key, region);
            if (loadedStamp != stamp) {
                region.markAllStyleDirty();
            } else {
                restyleBorders(region);
            }
            refreshNeighborBorders(region);
        }
        List<ChunkScanner.Result> scans = pending.remove(key);
        if (scans != null) {
            for (ChunkScanner.Result scan : scans) {
                write(region, scan);
            }
        }
        OverviewTile tile = overview.get(OverviewTile.key(Math.floorDiv(rx, OverviewTile.REGIONS), Math.floorDiv(rz, OverviewTile.REGIONS)));
        if (tile != null) {
            tile.put(rx, rz, region.thumbnail());
            region.thumbDirty = false;
        }
        revision++;
    }

    private void restyleBorders(RegionData r) {
        int m = RegionData.MASK;
        Stylizer.stylize(style, r, 0, 0, m, 1, neighbors);
        Stylizer.stylize(style, r, 0, m - 1, m, m, neighbors);
        Stylizer.stylize(style, r, 0, 2, 1, m - 2, neighbors);
        Stylizer.stylize(style, r, m - 1, 2, m, m - 2, neighbors);
    }

    private void refreshNeighborBorders(RegionData r) {
        int m = RegionData.MASK;
        markNeighbor(r.rx - 1, r.rz, m - 1, 0, m, m);
        markNeighbor(r.rx + 1, r.rz, 0, 0, 1, m);
        markNeighbor(r.rx, r.rz - 1, 0, m - 1, m, m);
        markNeighbor(r.rx, r.rz + 1, 0, 0, m, 1);
    }

    // ------------------------------------------------------------------ per tick

    public void tick(long budgetNanos, double playerX, double playerZ) {
        Runnable task;
        while ((task = completions.poll()) != null) {
            task.run();
        }
        restyle(budgetNanos, playerX, playerZ);
        pushThumbnails();
        long now = System.currentTimeMillis();
        if (now - lastAutosave > AUTOSAVE_MS) {
            lastAutosave = now;
            saveDirty();
        }
        evict(now, playerX, playerZ);
    }

    private void restyle(long budgetNanos, double px, double pz) {
        List<RegionData> dirty = null;
        for (RegionData r : regions.values()) {
            if (!r.styleDirty.isEmpty()) {
                if (dirty == null) {
                    dirty = new ArrayList<>();
                }
                dirty.add(r);
            }
        }
        if (dirty == null) {
            return;
        }
        int pcx = (int) Math.floor(px) >> RegionData.SHIFT;
        int pcz = (int) Math.floor(pz) >> RegionData.SHIFT;
        dirty.sort(Comparator.comparingInt(r -> Math.abs(r.rx - pcx) + Math.abs(r.rz - pcz)));
        long deadline = System.nanoTime() + budgetNanos;
        for (RegionData r : dirty) {
            while (!r.styleDirty.isEmpty()) {
                int x0 = r.styleDirty.x0();
                int z0 = r.styleDirty.z0();
                int x1 = r.styleDirty.x1();
                int z1 = r.styleDirty.z1();
                int zEnd = Math.min(z1, z0 + RESTYLE_ROWS - 1);
                Stylizer.stylize(style, r, x0, z0, x1, zEnd, neighbors);
                if (zEnd >= z1) {
                    r.styleDirty.clear();
                } else {
                    r.styleDirty.set(x0, zEnd + 1, x1, z1);
                }
                revision++;
                if (System.nanoTime() > deadline) {
                    return;
                }
            }
        }
    }

    private void pushThumbnails() {
        for (RegionData r : regions.values()) {
            if (r.thumbDirty) {
                r.thumbDirty = false;
                OverviewTile tile = overview.get(OverviewTile.key(Math.floorDiv(r.rx, OverviewTile.REGIONS), Math.floorDiv(r.rz, OverviewTile.REGIONS)));
                if (tile != null) {
                    tile.put(r.rx, r.rz, r.thumbnail());
                }
            }
        }
    }

    private void evict(long now, double px, double pz) {
        if (regions.size() > maxLoaded) {
            int pcx = (int) Math.floor(px) >> RegionData.SHIFT;
            int pcz = (int) Math.floor(pz) >> RegionData.SHIFT;
            List<RegionData> candidates = new ArrayList<>();
            for (RegionData r : regions.values()) {
                boolean nearPlayer = Math.abs(r.rx - pcx) <= 1 && Math.abs(r.rz - pcz) <= 1;
                if (!nearPlayer && now - r.lastTouched > EVICT_IDLE_MS) {
                    candidates.add(r);
                }
            }
            candidates.sort(Comparator.comparingLong(r -> r.lastTouched));
            int target = (int) (maxLoaded * 0.9f);
            for (RegionData r : candidates) {
                if (regions.size() <= target) {
                    break;
                }
                unload(r);
            }
        }
        if (!overview.isEmpty()) {
            List<Long> idle = new ArrayList<>();
            for (Long2ObjectMap.Entry<OverviewTile> e : overview.long2ObjectEntrySet()) {
                if (now - e.getValue().lastTouched > OVERVIEW_IDLE_MS) {
                    idle.add(e.getLongKey());
                }
            }
            for (long key : idle) {
                OverviewTile tile = overview.remove(key);
                tile.close();
                for (int j = 0; j < OverviewTile.REGIONS; j++) {
                    for (int i = 0; i < OverviewTile.REGIONS; i++) {
                        thumbRequested.remove(RegionData.key(tile.ox * OverviewTile.REGIONS + i, tile.oz * OverviewTile.REGIONS + j));
                    }
                }
            }
        }
    }

    private void unload(RegionData r) {
        if (r.saveDirty) {
            save(r);
        }
        long key = r.key();
        regions.remove(key);
        if (lookupCache == r) {
            lookupCache = null;
        }
        GuiTexture texture = textures.remove(key);
        if (texture != null) {
            texture.close();
        }
    }

    // ------------------------------------------------------------------ saving

    private void save(RegionData r) {
        if (!r.hasAnyExplored()) {
            r.saveDirty = false;
            return;
        }
        RegionFile.Snapshot snapshot = RegionFile.Snapshot.of(r);
        r.saveDirty = false;
        onDisk.add(r.key());
        Path path = regionPath(r.rx, r.rz);
        io.execute(() -> {
            try {
                RegionFile.write(path, snapshot);
            } catch (IOException e) {
                RemnantMinimaps.LOG.warn("Could not save map region {}", path, e);
            }
        });
    }

    public void saveDirty() {
        for (RegionData r : regions.values()) {
            if (r.saveDirty) {
                save(r);
            }
        }
    }

    /** Saves everything and frees GPU memory. The IO executor keeps running queued saves. */
    public void close() {
        Runnable task;
        while ((task = completions.poll()) != null) {
            task.run();
        }
        saveDirty();
        for (GuiTexture texture : textures.values()) {
            texture.close();
        }
        textures.clear();
        for (OverviewTile tile : overview.values()) {
            tile.close();
        }
        overview.clear();
        regions.clear();
        lookupCache = null;
    }

    // ------------------------------------------------------------------ style

    public void setStyle(MapStyle newStyle, int newMaxLoaded) {
        maxLoaded = newMaxLoaded;
        if (newStyle.stamp() == stamp) {
            return;
        }
        style = newStyle;
        stamp = newStyle.stamp();
        for (RegionData r : regions.values()) {
            r.markAllStyleDirty();
        }
        for (OverviewTile tile : overview.values()) {
            tile.close();
        }
        overview.clear();
        thumbRequested.clear();
    }

    // ------------------------------------------------------------------ rendering access

    /** GPU texture for a region, uploading pending changes. Null while unknown or loading. */
    public GuiTexture regionTexture(int rx, int rz) {
        long key = RegionData.key(rx, rz);
        RegionData r = regions.get(key);
        if (r == null) {
            requestLoad(rx, rz);
            return null;
        }
        r.lastTouched = System.currentTimeMillis();
        GuiTexture texture = textures.get(key);
        if (texture == null || texture.isClosed()) {
            texture = GuiTexture.create("remnantminimaps:region", RegionData.SIZE, RegionData.SIZE, RegionData.MIP_LEVELS);
            textures.put(key, texture);
            r.gpuDirty.addAll(RegionData.SIZE);
        }
        if (!r.gpuDirty.isEmpty()) {
            texture.uploadPyramid(r.mips, RegionData.SIZE, r.gpuDirty.x0(), r.gpuDirty.z0(), r.gpuDirty.x1(), r.gpuDirty.z1());
            r.gpuDirty.clear();
        }
        return texture;
    }

    /** Overview tile at overview coordinates (region >> 2), requesting thumbnails as needed. */
    public OverviewTile overviewTile(int ox, int oz) {
        long key = OverviewTile.key(ox, oz);
        OverviewTile tile = overview.get(key);
        if (tile == null) {
            boolean any = false;
            for (int j = 0; j < OverviewTile.REGIONS && !any; j++) {
                for (int i = 0; i < OverviewTile.REGIONS && !any; i++) {
                    any = isKnown(ox * OverviewTile.REGIONS + i, oz * OverviewTile.REGIONS + j);
                }
            }
            if (!any) {
                return null;
            }
            tile = new OverviewTile(ox, oz);
            overview.put(key, tile);
        }
        tile.lastTouched = System.currentTimeMillis();
        for (int j = 0; j < OverviewTile.REGIONS; j++) {
            for (int i = 0; i < OverviewTile.REGIONS; i++) {
                int rx = ox * OverviewTile.REGIONS + i;
                int rz = oz * OverviewTile.REGIONS + j;
                if (tile.has(rx, rz)) {
                    continue;
                }
                RegionData r = regions.get(RegionData.key(rx, rz));
                if (r != null) {
                    tile.put(rx, rz, r.thumbnail());
                } else if (indexReady && onDisk.contains(RegionData.key(rx, rz))) {
                    requestThumbnail(rx, rz);
                }
            }
        }
        return tile.isEmpty() ? null : tile;
    }

    private void requestThumbnail(int rx, int rz) {
        long key = RegionData.key(rx, rz);
        if (!thumbRequested.add(key)) {
            return;
        }
        MapStyle thumbStyle = style;
        int thumbStamp = stamp;
        Path path = regionPath(rx, rz);
        io.execute(() -> {
            int[] pixels = null;
            try {
                RegionFile.Thumbnail thumb = RegionFile.readThumbnail(path);
                pixels = thumb.pixels();
                if (thumb.styleStamp() != thumbStamp) {
                    RegionFile.Snapshot s = RegionFile.read(path);
                    RegionData tmp = new RegionData(rx, rz, s.colors(), s.heights(), s.depths());
                    Stylizer.stylizeAll(thumbStyle, tmp, Stylizer.EMPTY);
                    pixels = tmp.thumbnail().clone();
                    RegionFile.write(path, RegionFile.Snapshot.of(tmp));
                }
            } catch (IOException | RuntimeException e) {
                RemnantMinimaps.LOG.debug("Could not read thumbnail {}", path, e);
            }
            int[] result = pixels;
            completions.add(() -> {
                if (result == null || thumbStamp != stamp || regions.containsKey(key)) {
                    return;
                }
                OverviewTile tile = overview.get(OverviewTile.key(Math.floorDiv(rx, OverviewTile.REGIONS), Math.floorDiv(rz, OverviewTile.REGIONS)));
                if (tile != null) {
                    tile.put(rx, rz, result);
                    revision++;
                }
            });
        });
    }

    /** True when a column was explored (used for hover info). */
    public boolean explored(int wx, int wz) {
        return Columns.explored(columnColor(wx, wz));
    }
}
