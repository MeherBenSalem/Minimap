package io.nightbeam.remnantminimaps.core.teleport;

import io.nightbeam.remnantminimaps.core.teleport.SafeLanding.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeLandingTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 319;

    /** A column built bottom-up from a string: S floor, . open, W water, L danger (lava), one char per block. */
    private static SafeLanding.Column column(int baseY, String blocks) {
        return y -> {
            int i = y - baseY;
            if (i < 0) {
                return Cell.FLOOR; // bedrock and stone below the described part
            }
            if (i >= blocks.length()) {
                return Cell.OPEN;
            }
            return switch (blocks.charAt(i)) {
                case 'S' -> Cell.FLOOR;
                case 'W' -> Cell.WATER;
                case 'L' -> Cell.DANGER;
                default -> Cell.OPEN;
            };
        };
    }

    @Test
    void landsOnTopOfTheSurface() {
        SafeLanding.Column c = column(60, "SSSS...");
        assertEquals(64, SafeLanding.surface(c, 70, MIN_Y, MAX_Y));
    }

    @Test
    void landsOnTreeCanopyRatherThanUnderIt() {
        // ground at 62, trunk gap, leaves at 66 and 67
        SafeLanding.Column c = column(60, "SSS...SS..");
        assertEquals(68, SafeLanding.surface(c, 80, MIN_Y, MAX_Y));
    }

    @Test
    void floatsOnWaterSurface() {
        SafeLanding.Column c = column(50, "SSWWWW..");
        assertEquals(56, SafeLanding.surface(c, 60, MIN_Y, MAX_Y));
    }

    @Test
    void refusesLavaSurfaceInsteadOfDiggingIntoCaves() {
        SafeLanding.Column c = column(60, "S..SL...");
        assertEquals(SafeLanding.NONE, SafeLanding.surface(c, 70, MIN_Y, MAX_Y));
    }

    @Test
    void refusesTheVoid() {
        SafeLanding.Column c = y -> Cell.OPEN;
        assertEquals(SafeLanding.NONE, SafeLanding.surface(c, MAX_Y, 0, 255));
    }

    @Test
    void refusesWhenHeadWouldBeAboveBuildLimitButFeetAreNot() {
        // top block at the build limit: feet would be outside the world
        SafeLanding.Column c = y -> y <= 255 ? Cell.FLOOR : Cell.OPEN;
        assertEquals(SafeLanding.NONE, SafeLanding.surface(c, 255, 0, 255));
    }

    @Test
    void isSafeNeedsFloorAndTwoOpenBlocks() {
        SafeLanding.Column c = column(0, "S.S..");
        assertFalse(SafeLanding.isSafe(c, 1, -10, 100), "only one block of headroom");
        assertTrue(SafeLanding.isSafe(c, 3, -10, 100));
        assertFalse(SafeLanding.isSafe(c, 4, -10, 100), "no floor below");
    }

    @Test
    void neverLandsBelowTheWorld() {
        SafeLanding.Column c = y -> Cell.OPEN;
        assertFalse(SafeLanding.isSafe(c, 0, 0, 100));
        assertFalse(SafeLanding.isSafe(c, 1, 0, 100), "the void is not a floor");
    }

    @Test
    void netherLandsBelowTheHintInsideTheCave() {
        // bedrock floor 0-3, cave floor at 30, air 31-40, rock to 122, roof 123-127
        Map<Integer, Cell> cells = new HashMap<>();
        for (int y = 0; y <= 127; y++) {
            cells.put(y, y <= 30 || y > 40 ? Cell.FLOOR : Cell.OPEN);
        }
        SafeLanding.Column c = y -> cells.getOrDefault(y, Cell.OPEN);
        assertEquals(31, SafeLanding.nearHint(c, 36, 0, 127));
    }

    @Test
    void netherSearchesUpwardWhenNothingBelowAndNeverOnTheRoof() {
        Map<Integer, Cell> cells = new HashMap<>();
        for (int y = 0; y <= 127; y++) {
            cells.put(y, y >= 70 && y <= 80 ? Cell.OPEN : Cell.FLOOR);
        }
        SafeLanding.Column c = y -> cells.getOrDefault(y, Cell.OPEN);
        // hint deep inside rock, nothing open below: the cave above is found
        assertEquals(70, SafeLanding.nearHint(c, 20, 0, 127));
        // fully solid column up to the roof: open sky above the roof is never used
        SafeLanding.Column solid = y -> y <= 127 ? Cell.FLOOR : Cell.OPEN;
        assertEquals(SafeLanding.NONE, SafeLanding.nearHint(solid, 100, 0, 127));
    }

    @Test
    void netherAvoidsLavaFloors() {
        Map<Integer, Cell> cells = new HashMap<>();
        for (int y = 0; y <= 127; y++) {
            cells.put(y, Cell.FLOOR);
        }
        for (int y = 31; y <= 34; y++) {
            cells.put(y, Cell.OPEN);
        }
        cells.put(30, Cell.DANGER); // lava lake floor
        for (int y = 50; y <= 52; y++) {
            cells.put(y, Cell.OPEN);
        }
        SafeLanding.Column c = y -> cells.getOrDefault(y, Cell.OPEN);
        assertEquals(50, SafeLanding.nearHint(c, 33, 0, 127));
    }

    @Test
    void nearestPrefersTheClickedColumnThenTheClosestNeighbour() {
        SafeLanding.Lander lander = (x, z) -> (x == 10 && z == 10) ? 70 : (x == 12 && z == 10) ? 64 : SafeLanding.NONE;
        SafeLanding.Spot exact = SafeLanding.nearest(lander, 10, 10, 3);
        assertNotNull(exact);
        assertEquals(new SafeLanding.Spot(10, 70, 10), exact);

        SafeLanding.Lander onlyNeighbour = (x, z) -> (x == 12 && z == 10) || (x == 7 && z == 7) ? 64 : SafeLanding.NONE;
        assertEquals(new SafeLanding.Spot(12, 64, 10), SafeLanding.nearest(onlyNeighbour, 10, 10, 3));
    }

    @Test
    void nearestRespectsTheRadius() {
        SafeLanding.Lander far = (x, z) -> (x == 15 && z == 0) ? 64 : SafeLanding.NONE;
        assertNull(SafeLanding.nearest(far, 0, 0, 3));
        assertNull(SafeLanding.nearest(far, 0, 0, 0));
        assertNull(SafeLanding.nearest(far, 0, 0, 100), "radius is capped at MAX_RADIUS");
        SafeLanding.Lander near = (x, z) -> (x == 8 && z == 0) ? 64 : SafeLanding.NONE;
        assertEquals(new SafeLanding.Spot(8, 64, 0), SafeLanding.nearest(near, 0, 0, 100));
    }

    @Test
    void radiusZeroOnlyTriesTheColumnItself() {
        int[] calls = {0};
        SafeLanding.Lander counting = (x, z) -> {
            calls[0]++;
            return SafeLanding.NONE;
        };
        assertNull(SafeLanding.nearest(counting, 5, 5, 0));
        assertEquals(1, calls[0]);
    }
}
