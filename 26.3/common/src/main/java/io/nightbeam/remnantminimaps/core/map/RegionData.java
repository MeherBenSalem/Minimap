package io.nightbeam.remnantminimaps.core.map;

import io.nightbeam.remnantminimaps.core.style.Mipmaps;

/**
 * One 256x256 block area of a dimension: the raw scanned columns plus the stylized ARGB image
 * and its full mip pyramid. Mutated on the client thread only; the IO thread works on copies.
 */
public final class RegionData {
    public static final int SHIFT = 8;
    public static final int SIZE = 1 << SHIFT;
    public static final int MASK = SIZE - 1;
    public static final int AREA = SIZE * SIZE;
    public static final int MIP_LEVELS = SHIFT + 1;
    public static final int THUMB_LEVEL = 2;
    public static final int THUMB_SIZE = SIZE >> THUMB_LEVEL;

    public final int rx;
    public final int rz;
    public final int[] colors;
    public final short[] heights;
    public final byte[] depths;
    public final int[][] mips = new int[MIP_LEVELS][];

    /** Raw columns changed here; the styled image must be regenerated (local block coords). */
    public final DirtyRect styleDirty = new DirtyRect();
    /** Styled pixels changed here; the GPU copy must be refreshed (local block coords). */
    public final DirtyRect gpuDirty = new DirtyRect();
    public boolean thumbDirty;
    public boolean saveDirty;
    public int styleStamp = -1;
    public long lastTouched;

    public RegionData(int rx, int rz) {
        this(rx, rz, new int[AREA], new short[AREA], new byte[AREA]);
    }

    public RegionData(int rx, int rz, int[] colors, short[] heights, byte[] depths) {
        this.rx = rx;
        this.rz = rz;
        this.colors = colors;
        this.heights = heights;
        this.depths = depths;
        for (int level = 0; level < MIP_LEVELS; level++) {
            int size = SIZE >> level;
            mips[level] = new int[size * size];
        }
    }

    public static int index(int lx, int lz) {
        return (lz << SHIFT) | lx;
    }

    public static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }

    public long key() {
        return key(rx, rz);
    }

    public int minBlockX() {
        return rx << SHIFT;
    }

    public int minBlockZ() {
        return rz << SHIFT;
    }

    public int[] styled() {
        return mips[0];
    }

    public int[] thumbnail() {
        return mips[THUMB_LEVEL];
    }

    /**
     * Copies a 16x16 scanned chunk into this region. Returns true when anything changed.
     * Arrays are indexed z * 16 + x. Columns with color 0 (not scanned) are skipped.
     */
    public boolean writeChunk(int lx0, int lz0, int[] chunkColors, short[] chunkHeights, byte[] chunkDepths) {
        boolean changed = false;
        for (int z = 0; z < 16; z++) {
            int row = index(lx0, lz0 + z);
            for (int x = 0; x < 16; x++) {
                int src = z * 16 + x;
                int color = chunkColors[src];
                if (color == 0) {
                    continue;
                }
                int dst = row + x;
                if (colors[dst] != color || heights[dst] != chunkHeights[src] || depths[dst] != chunkDepths[src]) {
                    colors[dst] = color;
                    heights[dst] = chunkHeights[src];
                    depths[dst] = chunkDepths[src];
                    changed = true;
                }
            }
        }
        if (changed) {
            saveDirty = true;
            markStyleDirty(lx0 - 2, lz0 - 2, lx0 + 17, lz0 + 17);
        }
        return changed;
    }

    public void markStyleDirty(int x0, int z0, int x1, int z1) {
        x0 = Math.max(0, x0);
        z0 = Math.max(0, z0);
        x1 = Math.min(MASK, x1);
        z1 = Math.min(MASK, z1);
        if (x0 <= x1 && z0 <= z1) {
            styleDirty.add(x0, z0, x1, z1);
        }
    }

    public void markAllStyleDirty() {
        styleDirty.addAll(SIZE);
    }

    /** Recomputes the mip chain for the given level-0 rectangle and flags the GPU copy. */
    public void finishStyled(int x0, int z0, int x1, int z1) {
        Mipmaps.update(mips, SIZE, x0, z0, x1, z1);
        gpuDirty.add(x0, z0, x1, z1);
        thumbDirty = true;
    }

    public boolean hasAnyExplored() {
        for (int color : colors) {
            if (color != 0) {
                return true;
            }
        }
        return false;
    }
}
