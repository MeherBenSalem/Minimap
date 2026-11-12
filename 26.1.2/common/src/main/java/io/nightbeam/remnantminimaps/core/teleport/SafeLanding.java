package io.nightbeam.remnantminimaps.core.teleport;

/**
 * Chooses where a player lands when teleporting to a map column. Pure logic: the caller loads the
 * chunk first and then describes each block of a column as a {@link Cell}.
 *
 * <p>A landing spot is a feet position whose block below is something to stand on (or water to
 * fall into) and whose feet and head blocks are open and harmless.
 */
public final class SafeLanding {
    /** Returned when a column has no safe landing spot. */
    public static final int NONE = Integer.MIN_VALUE;
    /** Largest search radius around the requested column, in blocks. */
    public static final int MAX_RADIUS = 8;

    /** How a single block matters to a landing player. */
    public enum Cell {
        /** Nothing to collide with and nothing harmful: air, grass tufts, flowers, open doors. */
        OPEN,
        /** Something to stand on that does not hurt: stone, grass, leaves, slabs, glass. */
        FLOOR,
        /** Water. Falling into it is safe, standing inside it is not a landing spot. */
        WATER,
        /** Hurts or traps: lava, fire, magma, cactus, powder snow, berry bushes, wither roses. */
        DANGER
    }

    /** The blocks of one column. Called only for heights between the column's limits. */
    @FunctionalInterface
    public interface Column {
        Cell cell(int y);
    }

    /** Finds the landing height of the column at a position, or {@link #NONE}. */
    @FunctionalInterface
    public interface Lander {
        int landingY(int x, int z);
    }

    /** A chosen landing spot: block coordinates of the player's feet. */
    public record Spot(int x, int y, int z) {}

    /** Offsets within {@link #MAX_RADIUS}, nearest first (ties in a fixed order). */
    private static final int[][] OFFSETS = buildOffsets();

    private SafeLanding() {}

    private static int[][] buildOffsets() {
        int side = MAX_RADIUS * 2 + 1;
        int[][] all = new int[side * side][];
        int n = 0;
        for (int dz = -MAX_RADIUS; dz <= MAX_RADIUS; dz++) {
            for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++) {
                all[n++] = new int[] {dx, dz};
            }
        }
        java.util.Arrays.sort(all, java.util.Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1])
                .thenComparingInt(o -> o[1]).thenComparingInt(o -> o[0]));
        return all;
    }

    private static Cell at(Column column, int y, int minY, int maxY) {
        if (y < minY) {
            return Cell.DANGER; // the void
        }
        if (y > maxY) {
            return Cell.OPEN; // open sky above the build limit
        }
        return column.cell(y);
    }

    /** True when a player whose feet are at {@code feetY} would stand (or float) safely. */
    public static boolean isSafe(Column column, int feetY, int minY, int maxY) {
        if (feetY <= minY || feetY > maxY) {
            return false;
        }
        Cell floor = at(column, feetY - 1, minY, maxY);
        if (floor != Cell.FLOOR && floor != Cell.WATER) {
            return false;
        }
        return at(column, feetY, minY, maxY) == Cell.OPEN && at(column, feetY + 1, minY, maxY) == Cell.OPEN;
    }

    /**
     * Landing for dimensions with an open sky: on top of the column's highest block. Never digs
     * into caves below; when the top block is dangerous (lava, fire, magma) there is no landing.
     *
     * @param top  highest Y that may hold a block (for example the world-surface heightmap)
     * @param minY lowest block of the world
     * @param maxY highest block of the world
     */
    public static int surface(Column column, int top, int minY, int maxY) {
        for (int y = Math.min(top, maxY); y >= minY; y--) {
            if (column.cell(y) == Cell.OPEN) {
                continue;
            }
            int feet = y + 1;
            return isSafe(column, feet, minY, maxY) ? feet : NONE;
        }
        return NONE; // nothing but air: the void
    }

    /**
     * Landing for dimensions with a ceiling (the Nether): the first safe spot at or below
     * {@code hintY}, else the first one above it, never above {@code maxY} (so never on the roof).
     *
     * @param hintY feet height to try first, usually the map's height for the column plus one
     * @param minY  lowest block of the world
     * @param maxY  highest block a player may be in (the top of the dimension's logical height)
     */
    public static int nearHint(Column column, int hintY, int minY, int maxY) {
        int start = Math.max(minY + 1, Math.min(maxY, hintY));
        for (int y = start; y > minY; y--) {
            if (isSafe(column, y, minY, maxY)) {
                return y;
            }
        }
        for (int y = start + 1; y <= maxY; y++) {
            if (isSafe(column, y, minY, maxY)) {
                return y;
            }
        }
        return NONE;
    }

    /**
     * The safe spot closest to {@code (x, z)}, looking at most {@code radius} blocks away
     * (capped at {@link #MAX_RADIUS}). Null when there is none.
     */
    public static Spot nearest(Lander lander, int x, int z, int radius) {
        int r = Math.max(0, Math.min(MAX_RADIUS, radius));
        int limit = r * r;
        for (int[] offset : OFFSETS) {
            int dx = offset[0];
            int dz = offset[1];
            if (Math.abs(dx) > r || Math.abs(dz) > r || dx * dx + dz * dz > limit) {
                continue;
            }
            int y = lander.landingY(x + dx, z + dz);
            if (y != NONE) {
                return new Spot(x + dx, y, z + dz);
            }
        }
        return null;
    }
}
