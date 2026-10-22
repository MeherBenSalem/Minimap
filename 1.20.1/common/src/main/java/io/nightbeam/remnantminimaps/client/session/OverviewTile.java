package io.nightbeam.remnantminimaps.client.session;

import io.nightbeam.remnantminimaps.client.render.gfx.GuiTexture;
import io.nightbeam.remnantminimaps.core.map.DirtyRect;
import io.nightbeam.remnantminimaps.core.map.RegionData;
import io.nightbeam.remnantminimaps.core.style.Mipmaps;

/**
 * A 4x4 block of region thumbnails (1024x1024 blocks at 4 blocks per texel), used when the world
 * map is zoomed far out so we draw a handful of textures instead of hundreds.
 */
public final class OverviewTile {
    public static final int REGIONS = 4;
    public static final int SIZE = REGIONS * RegionData.THUMB_SIZE;
    public static final int BLOCKS = REGIONS * RegionData.SIZE;
    private static final int LEVELS = 9;

    public final int ox;
    public final int oz;
    private final int[][] pyramid = new int[LEVELS][];
    private final DirtyRect gpuDirty = new DirtyRect();
    private GuiTexture texture;
    private int filled;
    public long lastTouched;

    OverviewTile(int ox, int oz) {
        this.ox = ox;
        this.oz = oz;
        for (int l = 0; l < LEVELS; l++) {
            int s = SIZE >> l;
            pyramid[l] = new int[s * s];
        }
    }

    public static long key(int ox, int oz) {
        return RegionData.key(ox, oz);
    }

    /** Places one region's 64x64 thumbnail. (rx, rz) are absolute region coordinates. */
    void put(int rx, int rz, int[] thumb) {
        int i = Math.floorMod(rx, REGIONS);
        int j = Math.floorMod(rz, REGIONS);
        int x0 = i * RegionData.THUMB_SIZE;
        int y0 = j * RegionData.THUMB_SIZE;
        int[] base = pyramid[0];
        for (int row = 0; row < RegionData.THUMB_SIZE; row++) {
            System.arraycopy(thumb, row * RegionData.THUMB_SIZE, base, (y0 + row) * SIZE + x0, RegionData.THUMB_SIZE);
        }
        int x1 = x0 + RegionData.THUMB_SIZE - 1;
        int y1 = y0 + RegionData.THUMB_SIZE - 1;
        Mipmaps.update(pyramid, SIZE, x0, y0, x1, y1);
        gpuDirty.add(x0, y0, x1, y1);
        filled |= 1 << (j * REGIONS + i);
    }

    boolean has(int rx, int rz) {
        int i = Math.floorMod(rx, REGIONS);
        int j = Math.floorMod(rz, REGIONS);
        return (filled & (1 << (j * REGIONS + i))) != 0;
    }

    /** Returns the GPU texture, creating and refreshing it as needed. Render thread only. */
    public GuiTexture texture() {
        if (texture == null) {
            texture = GuiTexture.create("remnantminimaps:overview", SIZE, SIZE, LEVELS);
            gpuDirty.addAll(SIZE);
        }
        if (!gpuDirty.isEmpty()) {
            texture.uploadPyramid(pyramid, SIZE, gpuDirty.x0(), gpuDirty.z0(), gpuDirty.x1(), gpuDirty.z1());
            gpuDirty.clear();
        }
        return texture;
    }

    public boolean isEmpty() {
        return filled == 0;
    }

    void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
