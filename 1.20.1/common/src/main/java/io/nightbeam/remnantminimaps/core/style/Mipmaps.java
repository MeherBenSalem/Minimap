package io.nightbeam.remnantminimaps.core.style;

/** Alpha-weighted box-filter mip generation for square ARGB pyramids. */
public final class Mipmaps {
    private Mipmaps() {}

    /**
     * Rebuilds every level above 0 for the area covered by the inclusive level-0 rectangle.
     */
    public static void update(int[][] levels, int size, int x0, int z0, int x1, int z1) {
        for (int level = 1; level < levels.length; level++) {
            int dstSize = size >> level;
            if (dstSize < 1) {
                break;
            }
            int srcSize = dstSize << 1;
            int[] src = levels[level - 1];
            int[] dst = levels[level];
            int lx0 = Math.max(0, x0 >> level);
            int lz0 = Math.max(0, z0 >> level);
            int lx1 = Math.min(dstSize - 1, x1 >> level);
            int lz1 = Math.min(dstSize - 1, z1 >> level);
            for (int z = lz0; z <= lz1; z++) {
                int srcRow = (z << 1) * srcSize;
                for (int x = lx0; x <= lx1; x++) {
                    int s = srcRow + (x << 1);
                    dst[z * dstSize + x] = average(src[s], src[s + 1], src[s + srcSize], src[s + srcSize + 1]);
                }
            }
        }
    }

    public static void rebuild(int[][] levels, int size) {
        update(levels, size, 0, 0, size - 1, size - 1);
    }

    public static int average(int c0, int c1, int c2, int c3) {
        int a0 = c0 >>> 24;
        int a1 = c1 >>> 24;
        int a2 = c2 >>> 24;
        int a3 = c3 >>> 24;
        int aSum = a0 + a1 + a2 + a3;
        if (aSum == 0) {
            return 0;
        }
        int r = (((c0 >> 16) & 0xFF) * a0 + ((c1 >> 16) & 0xFF) * a1 + ((c2 >> 16) & 0xFF) * a2 + ((c3 >> 16) & 0xFF) * a3) / aSum;
        int g = (((c0 >> 8) & 0xFF) * a0 + ((c1 >> 8) & 0xFF) * a1 + ((c2 >> 8) & 0xFF) * a2 + ((c3 >> 8) & 0xFF) * a3) / aSum;
        int b = ((c0 & 0xFF) * a0 + (c1 & 0xFF) * a1 + (c2 & 0xFF) * a2 + (c3 & 0xFF) * a3) / aSum;
        int a = (aSum + 2) >> 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
