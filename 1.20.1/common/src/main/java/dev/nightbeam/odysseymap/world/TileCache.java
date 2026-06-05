package dev.nightbeam.odysseymap.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class TileCache {
    private final ColumnSampler sampler;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private ResourceKey<Level> currentDimension;

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

    public Tile getOrCreate(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        if (currentDimension != null && !currentDimension.equals(dimension)) clear();
        currentDimension = dimension;
        long key = tileKey(dimension, alignedX, alignedZ);
        return tiles.computeIfAbsent(key, k -> new Tile());
    }

    public Tile get(ResourceKey<Level> dimension, int alignedX, int alignedZ) {
        return tiles.get(tileKey(dimension, alignedX, alignedZ));
    }

    public void samplePixel(Level level, int worldX, int worldZ) {
        ResourceKey<Level> dim = level.dimension();
        int alignedX = alignTile(worldX);
        int alignedZ = alignTile(worldZ);
        Tile tile = getOrCreate(dim, alignedX, alignedZ);
        int localX = worldX - alignedX + 64;
        int localZ = worldZ - alignedZ + 64;
        if (localX < 0 || localZ < 0 || localX >= Tile.SIZE || localZ >= Tile.SIZE) return;
        if (!level.hasChunkAt(worldX, worldZ)) {
            tile.setPixel(localX, localZ, 0x00000000);
            tile.markDirty();
            return;
        }
        int color = sampler.sampleColumn(level, worldX, worldZ);
        tile.setPixel(localX, localZ, color);
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

    public void markAllDirty() { tiles.values().forEach(Tile::markDirty); }

    public void clear() { tiles.clear(); currentDimension = null; }

    public Map<Long, Tile> getTiles() { return tiles; }
}
