package dev.nightbeam.odysseymap.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;

public class TileCache {
    private static final int MAX_RESIDENT_TILES = 256;
    private static final int MAX_RENDER_REQUESTS = 512 * 512;
    private static final Logger LOG = LoggerFactory.getLogger("OdysseyMap");
    private final ColumnSampler sampler;
    private final Map<Long, Tile> tiles = new ConcurrentHashMap<>();
    private volatile ResourceKey<Level> currentDimension;
    private final Map<Long, StoredTile> addresses = new LinkedHashMap<>(16, 0.75f, true);
    // Failed writes retain their original world/dimension destination across unloads.
    private final Map<Path, StoredTile> pending = new LinkedHashMap<>();
    private final Set<Path> blockedWrites = new HashSet<>();
    private final Set<Path> unreadableTiles = new HashSet<>();
    private final Set<Long> renderedTiles = new HashSet<>();
    private final Map<Long, RenderRequest> renderRequests = new LinkedHashMap<>();
    private Set<Long> diskTileKeys;
    private RenderView renderView;
    private volatile long contentVersion;
    private final Set<Path> reportedFailures = new HashSet<>();
    private Path storageRoot;
    private int saveCursor;

    public record RenderView(ResourceKey<Level> dimension, int width, int height,
                             int centerX, int centerZ, int stride, boolean masked) {}
    private record RenderRequest(String dimension, int x, int z) {}

    private record StoredTile(Path path, String dimension, int x, int z, Tile tile) {}

    public TileCache(ColumnSampler sampler) { this.sampler = sampler; }

    public static long tileKey(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        int dim = dimension.location().hashCode();
        return ((long) dim << 52)
                | ((long) (alignedZ < 0 ? 1 : 0) << 51)
                | ((long) (Math.abs(alignedZ) >> 7) << 26)
                | ((long) (alignedX < 0 ? 1 : 0) << 25)
                | (Math.abs(alignedX) >> 7);
    }

    public static int alignTile(int blockCoord) {
        return ((blockCoord + 64) >> 7) << 7;
    }

    public synchronized void setStorageRoot(Path root) {
        Path normalized = root == null ? null : root.toAbsolutePath().normalize();
        if (java.util.Objects.equals(storageRoot, normalized)) return;
        clear();
        storageRoot = normalized;
    }

    public synchronized Tile getOrCreate(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        activateDimension(dimension);
        long key = tileKey(dimension, alignedX, alignedZ);
        renderRequests.remove(key);
        Tile existing = tiles.get(key);
        if (existing != null) {
            addresses.get(key);
            return existing;
        }
        Tile tile = new Tile();
        if (storageRoot != null) {
            String dimensionId = dimension.location().toString();
            Path path = TileStorage.tilePath(storageRoot, dimensionId, alignedX, alignedZ);
            StoredTile retained = pending.remove(path);
            if (retained != null) {
                tile = retained.tile();
            } else {
                try {
                    tile = TileStorage.load(path, dimensionId, alignedX, alignedZ);
                } catch (TileStorage.InvalidTileException e) {
                    // Never replace corrupt or newer-format data with an empty/partial tile.
                    blockedWrites.add(path);
                    LOG.error("Cannot load explored map tile; preserving the existing file: {}", path, e);
                } catch (IOException e) {
                    unreadableTiles.add(path);
                    LOG.error("Cannot read explored map tile; retaining new exploration until it can be retried: {}", path, e);
                }
            }
            addresses.put(key, new StoredTile(path, dimensionId, alignedX, alignedZ, tile));
        }
        tiles.put(key, tile);
        trimCache(key);
        return tile;
    }

    private void activateDimension(ResourceKey<Level> dimension) {
        if (currentDimension != null && !currentDimension.equals(dimension)) clear();
        if (dimension.equals(currentDimension)) return;
        currentDimension = dimension;
        diskTileKeys = new HashSet<>();
        if (storageRoot != null) {
            try {
                for (TileStorage.Coordinates coordinates : TileStorage.listTiles(storageRoot, dimension.location().toString())) {
                    diskTileKeys.add(tileKey(dimension, coordinates.x(), coordinates.z()));
                }
            } catch (IOException e) {
                // An unavailable index must not classify stored terrain as absent.
                diskTileKeys = null;
                LOG.warn("Cannot index explored map tiles; retrying requested tiles individually", e);
            }
        }
    }

    /** Start a viewport. Completed pixels are retained in the composed texture after tile eviction. */
    public synchronized boolean beginRender(RenderView view) {
        activateDimension(view.dimension());
        if (view.equals(renderView)) return false;
        renderView = view;
        renderRequests.clear();
        renderedTiles.clear();
        return true;
    }

    /** Rendering queues cold tiles; disk reads happen in a bounded client-tick budget. */
    public synchronized Tile getForRender(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        activateDimension(dimension);
        long key = tileKey(dimension, alignedX, alignedZ);
        Tile existing = tiles.get(key);
        if (existing != null) {
            renderRequests.remove(key);
            addresses.get(key);
            renderedTiles.add(key);
            return existing;
        }
        if (renderedTiles.contains(key) || renderRequests.containsKey(key) || storageRoot == null) return null;
        String id = dimension.location().toString();
        Path path = TileStorage.tilePath(storageRoot, id, alignedX, alignedZ);
        if (diskTileKeys != null && !diskTileKeys.contains(key) && !pending.containsKey(path)) {
            renderedTiles.add(key);
            return null;
        }
        if (renderRequests.size() < MAX_RENDER_REQUESTS) {
            renderRequests.putIfAbsent(key, new RenderRequest(id, alignedX, alignedZ));
        }
        return null;
    }

    public synchronized void loadRenderTiles(int maxTiles) {
        int budget = Math.min(maxTiles, renderRequests.size());
        for (int i = 0; i < budget; i++) {
            var iterator = renderRequests.entrySet().iterator();
            if (!iterator.hasNext()) break;
            var entry = iterator.next();
            long key = entry.getKey();
            RenderRequest request = entry.getValue();
            iterator.remove();
            if (tiles.containsKey(key)) continue;
            Path path = TileStorage.tilePath(storageRoot, request.dimension(), request.x(), request.z());
            if (blockedWrites.contains(path)) { renderedTiles.add(key); continue; }
            try {
                StoredTile retained = pending.remove(path);
                Tile tile = retained != null ? retained.tile()
                        : TileStorage.load(path, request.dimension(), request.x(), request.z());
                tiles.put(key, tile);
                addresses.put(key, new StoredTile(path, request.dimension(), request.x(), request.z(), tile));
                reportedFailures.remove(path);
                trimCache(key);
                contentVersion++;
            } catch (TileStorage.InvalidTileException e) {
                blockedWrites.add(path);
                renderedTiles.add(key);
                LOG.error("Cannot render explored map tile; preserving its existing file: {}", path, e);
            } catch (IOException e) {
                renderRequests.put(key, request);
                if (reportedFailures.add(path)) LOG.warn("Cannot read explored map tile; will retry: {}", path, e);
            }
        }
    }

    public long getContentVersion() { return contentVersion; }

    public synchronized Tile get(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        if (!dimension.equals(currentDimension)) return null;
        return tiles.get(tileKey(dimension, alignedX, alignedZ));
    }

    public synchronized void samplePixel(Level level, int worldX, int worldZ) {
        // Unloaded columns are unknown, not evidence that previously explored terrain vanished.
        if (!level.hasChunkAt(worldX, worldZ)) return;
        ResourceKey<Level> dim = level.dimension();
        int alignedX = alignTile(worldX);
        int alignedZ = alignTile(worldZ);
        Tile tile = getOrCreate(dim, alignedX, alignedZ);
        int localX = worldX - alignedX + 64;
        int localZ = worldZ - alignedZ + 64;
        if (localX < 0 || localZ < 0 || localX >= Tile.SIZE || localZ >= Tile.SIZE) return;
        int color = sampler.sampleColumn(level, worldX, worldZ);
        int previous = tile.getPixel(localX, localZ);
        tile.setPixel(localX, localZ, color);
        if (previous != color) {
            renderedTiles.remove(tileKey(dim, alignedX, alignedZ));
            contentVersion++;
        }
        tile.setLastUpdatedTick(level.getGameTime());
    }

    public void invalidateArea(Level level, int minX, int minZ, int maxX, int maxZ) {
        ResourceKey<Level> dim = level.dimension();
        for (int x = alignTile(minX); x <= alignTile(maxX); x += 128) {
            for (int z = alignTile(minZ); z <= alignTile(maxZ); z += 128) {
                Tile tile = get(dim, x, z);
                if (tile != null) tile.markDirty();
            }
        }
    }

    public synchronized void markAllDirty() {
        tiles.values().forEach(Tile::markDirty);
        renderView = null;
        renderRequests.clear();
        renderedTiles.clear();
        contentVersion++;
    }

    public synchronized void flush(int maxTiles) {
        var candidates = new ArrayList<>(pending.values());
        candidates.addAll(addresses.values());
        if (candidates.isEmpty()) return;
        int attempted = 0;
        int start = Math.floorMod(saveCursor, candidates.size());
        for (int scanned = 0; scanned < candidates.size() && attempted < maxTiles; scanned++) {
            int index = (start + scanned) % candidates.size();
            StoredTile stored = candidates.get(index);
            saveCursor = (index + 1) % candidates.size();
            if (blockedWrites.contains(stored.path())) continue;
            if (unreadableTiles.contains(stored.path())) {
                if (recoverRead(stored) && (!stored.tile().needsSaving() || save(stored))) pending.remove(stored.path(), stored);
                attempted++;
            } else if (!stored.tile().needsSaving()) {
                pending.remove(stored.path(), stored);
            } else {
                if (save(stored)) pending.remove(stored.path(), stored);
                attempted++;
            }
        }
        // Round-robin attempts ensure failed old-world writes cannot starve healthy tiles.
    }

    private boolean save(StoredTile stored) {
        if (blockedWrites.contains(stored.path())) return false;
        if (unreadableTiles.contains(stored.path()) && !recoverRead(stored)) return false;
        Tile.Snapshot snapshot = stored.tile().snapshot();
        try {
            TileStorage.save(stored.path(), stored.dimension(), stored.x(), stored.z(), snapshot);
            stored.tile().markSaved(snapshot.revision());
            reportedFailures.remove(stored.path());
            if (currentDimension != null && storageRoot != null && stored.dimension().equals(currentDimension.location().toString())
                    && stored.path().equals(TileStorage.tilePath(storageRoot, stored.dimension(), stored.x(), stored.z()))) {
                long key = tileKey(currentDimension, stored.x(), stored.z());
                if (diskTileKeys != null) diskTileKeys.add(key);
                renderedTiles.remove(key);
                contentVersion++;
            }
            return !stored.tile().needsSaving();
        } catch (IOException e) {
            if (reportedFailures.add(stored.path())) {
                LOG.error("Cannot save explored map tile; retaining it in memory for retry: {}", stored.path(), e);
            }
            return false;
        }
    }

    private boolean recoverRead(StoredTile stored) {
        try {
            Tile previous = TileStorage.load(stored.path(), stored.dimension(), stored.x(), stored.z());
            stored.tile().mergeExisting(previous.snapshot().pixels());
            unreadableTiles.remove(stored.path());
            reportedFailures.remove(stored.path());
            if (currentDimension != null && storageRoot != null
                    && stored.path().equals(TileStorage.tilePath(storageRoot, stored.dimension(), stored.x(), stored.z()))) {
                renderedTiles.remove(tileKey(currentDimension, stored.x(), stored.z()));
            }
            contentVersion++;
            return true;
        } catch (TileStorage.InvalidTileException e) {
            blockedWrites.add(stored.path());
            LOG.error("Cannot recover explored map tile; preserving its file: {}", stored.path(), e);
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private void trimCache(long newestKey) {
        if (tiles.size() <= MAX_RESIDENT_TILES) return;
        var iterator = addresses.entrySet().iterator();
        boolean attemptedWrite = false;
        while (tiles.size() > MAX_RESIDENT_TILES && iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() == newestKey) continue;
            StoredTile stored = entry.getValue();
            if (!stored.tile().needsSaving() && !unreadableTiles.contains(stored.path())) {
                tiles.remove(entry.getKey());
                iterator.remove();
            } else if (!attemptedWrite && !blockedWrites.contains(stored.path())
                    && !unreadableTiles.contains(stored.path()) && !reportedFailures.contains(stored.path())) {
                attemptedWrite = true;
                if (save(stored)) {
                    tiles.remove(entry.getKey());
                    iterator.remove();
                }
            }
        }
        // Retaining unsaved exploration takes precedence over the memory cap during disk failure.
    }

    public synchronized void clear() {
        flush(Integer.MAX_VALUE);
        for (StoredTile stored : addresses.values()) {
            if (stored.tile().needsSaving() || unreadableTiles.contains(stored.path())) pending.put(stored.path(), stored);
        }
        tiles.clear();
        addresses.clear();
        renderRequests.clear();
        renderedTiles.clear();
        renderView = null;
        diskTileKeys = null;
        contentVersion++;
        currentDimension = null;
    }

    public synchronized void endSession() {
        clear();
        storageRoot = null;
    }

    public Map<Long, Tile> getTiles() { return tiles; }
}
