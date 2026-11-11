package io.nightbeam.remnantminimaps.core.preload;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * The chunks a pre-load visits: every chunk whose center lies within a circle, grouped by map region
 * (16 x 16 chunks) and with the regions nearest the center first. Working one region at a time keeps
 * the map's memory use flat. Positions are packed like {@link #pack}. Pure logic.
 */
public final class PreloadPlan {
    /** Chunks per map region side (a map region is 256 x 256 blocks). */
    public static final int REGION_CHUNKS = 16;

    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final long radiusSq;
    private final long[] regions;
    private final int total;

    private int regionIndex;
    private int cellIndex = -1;
    private long nextPos;
    private boolean hasNext;
    private int handedOut;

    /**
     * @param centerX chunk x of the center
     * @param centerZ chunk z of the center
     * @param radius  radius in chunks, 0 or more
     */
    public PreloadPlan(int centerX, int centerZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius < 0");
        }
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.radiusSq = (long) radius * radius;

        int rx0 = Math.floorDiv(centerX - radius, REGION_CHUNKS);
        int rx1 = Math.floorDiv(centerX + radius, REGION_CHUNKS);
        int rz0 = Math.floorDiv(centerZ - radius, REGION_CHUNKS);
        int rz1 = Math.floorDiv(centerZ + radius, REGION_CHUNKS);
        long[] all = new long[(rx1 - rx0 + 1) * (rz1 - rz0 + 1)];
        int n = 0;
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                all[n++] = pack(rx, rz);
            }
        }
        int crx = Math.floorDiv(centerX, REGION_CHUNKS);
        int crz = Math.floorDiv(centerZ, REGION_CHUNKS);
        Long[] boxed = Arrays.stream(all).boxed().toArray(Long[]::new);
        Arrays.sort(boxed, (a, b) -> {
            long da = distSq(unpackX(a) - crx, unpackZ(a) - crz);
            long db = distSq(unpackX(b) - crx, unpackZ(b) - crz);
            if (da != db) {
                return Long.compare(da, db);
            }
            return Long.compare(a, b);
        });
        this.regions = Arrays.stream(boxed).mapToLong(Long::longValue).toArray();

        int count = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (distSq(dx, dz) <= radiusSq) {
                    count++;
                }
            }
        }
        this.total = count;
        advance();
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    private static long distSq(long dx, long dz) {
        return dx * dx + dz * dz;
    }

    private boolean inside(int x, int z) {
        return distSq(x - centerX, z - centerZ) <= radiusSq;
    }

    private void advance() {
        while (regionIndex < regions.length) {
            long region = regions[regionIndex];
            int baseX = unpackX(region) * REGION_CHUNKS;
            int baseZ = unpackZ(region) * REGION_CHUNKS;
            while (++cellIndex < REGION_CHUNKS * REGION_CHUNKS) {
                int x = baseX + (cellIndex % REGION_CHUNKS);
                int z = baseZ + (cellIndex / REGION_CHUNKS);
                if (inside(x, z)) {
                    nextPos = pack(x, z);
                    hasNext = true;
                    return;
                }
            }
            regionIndex++;
            cellIndex = -1;
        }
        hasNext = false;
    }

    public boolean hasNext() {
        return hasNext;
    }

    /** The next chunk position, packed with {@link #pack}. */
    public long next() {
        if (!hasNext) {
            throw new NoSuchElementException();
        }
        long pos = nextPos;
        handedOut++;
        advance();
        return pos;
    }

    /** Number of chunks in the plan. */
    public int total() {
        return total;
    }

    /** Number of positions {@link #next} has returned so far. */
    public int handedOut() {
        return handedOut;
    }

    public int radius() {
        return radius;
    }
}
