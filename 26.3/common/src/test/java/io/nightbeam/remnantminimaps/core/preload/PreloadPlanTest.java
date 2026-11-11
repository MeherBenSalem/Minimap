package io.nightbeam.remnantminimaps.core.preload;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreloadPlanTest {
    private static Set<Long> drain(PreloadPlan plan) {
        Set<Long> seen = new HashSet<>();
        while (plan.hasNext()) {
            assertTrue(seen.add(plan.next()), "no chunk is visited twice");
        }
        return seen;
    }

    @Test
    void radiusZeroIsTheCenterChunkOnly() {
        PreloadPlan plan = new PreloadPlan(5, -7, 0);
        assertEquals(1, plan.total());
        assertEquals(PreloadPlan.pack(5, -7), plan.next());
        assertFalse(plan.hasNext());
    }

    @Test
    void coversExactlyTheChunksInsideTheCircle() {
        int cx = 3;
        int cz = -40;
        int r = 20;
        PreloadPlan plan = new PreloadPlan(cx, cz, r);
        Set<Long> seen = drain(plan);
        assertEquals(plan.total(), seen.size());
        assertEquals(plan.total(), plan.handedOut());
        for (int dz = -r - 2; dz <= r + 2; dz++) {
            for (int dx = -r - 2; dx <= r + 2; dx++) {
                boolean inside = dx * dx + dz * dz <= r * r;
                assertEquals(inside, seen.contains(PreloadPlan.pack(cx + dx, cz + dz)), "chunk " + dx + "," + dz);
            }
        }
    }

    @Test
    void finishesOneRegionBeforeStartingTheNextAndStartsAtTheCenterRegion() {
        PreloadPlan plan = new PreloadPlan(40, 40, 30);
        long first = plan.next();
        assertEquals(2, Math.floorDiv(PreloadPlan.unpackX(first), 16));
        assertEquals(2, Math.floorDiv(PreloadPlan.unpackZ(first), 16));
        Set<Long> finishedRegions = new HashSet<>();
        long currentRegion = PreloadPlan.pack(2, 2);
        while (plan.hasNext()) {
            long pos = plan.next();
            long region = PreloadPlan.pack(Math.floorDiv(PreloadPlan.unpackX(pos), 16), Math.floorDiv(PreloadPlan.unpackZ(pos), 16));
            if (region != currentRegion) {
                assertTrue(finishedRegions.add(currentRegion), "a region is never revisited");
                currentRegion = region;
            }
            assertFalse(finishedRegions.contains(region));
        }
    }

    @Test
    void workAcrossNegativeCoordinatesAndPackingRoundTrips() {
        long p = PreloadPlan.pack(-123456, 987654);
        assertEquals(-123456, PreloadPlan.unpackX(p));
        assertEquals(987654, PreloadPlan.unpackZ(p));
        PreloadPlan plan = new PreloadPlan(-1, -1, 3);
        assertEquals(plan.total(), drain(plan).size());
    }

    @Test
    void largestAllowedRadiusIsCheapToPlan() {
        // 4096 blocks = 256 chunks, the largest radius the settings allow.
        PreloadPlan plan = new PreloadPlan(0, 0, 256);
        assertTrue(plan.total() > 200_000 && plan.total() < 210_000, "about pi * 256^2 chunks: " + plan.total());
    }

    @Test
    void rejectsNegativeRadiusAndExhaustion() {
        assertThrows(IllegalArgumentException.class, () -> new PreloadPlan(0, 0, -1));
        PreloadPlan plan = new PreloadPlan(0, 0, 0);
        plan.next();
        assertThrows(java.util.NoSuchElementException.class, plan::next);
    }
}
