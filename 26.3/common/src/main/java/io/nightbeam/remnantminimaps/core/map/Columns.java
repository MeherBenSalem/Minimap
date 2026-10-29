package io.nightbeam.remnantminimaps.core.map;

/**
 * Packing helpers for a mapped column. A column color int stores the base RGB in the low 24 bits
 * and the surface category in the next 4 bits. A value of 0 means the column was never explored.
 */
public final class Columns {
    public static final int NONE = 0;
    public static final int GROUND = 1;
    public static final int GRASS = 2;
    public static final int FOLIAGE = 3;
    public static final int WATER = 4;
    public static final int LAVA = 5;
    public static final int SAND = 6;
    public static final int SNOW = 7;
    public static final int STONE = 8;
    public static final int BUILDING = 9;
    public static final int PATH = 10;
    public static final int FIELD = 11;
    public static final int VOID = 12;

    private Columns() {}

    public static int pack(int category, int rgb) {
        return ((category & 0x0F) << 24) | (rgb & 0xFFFFFF);
    }

    public static int category(int packed) {
        return (packed >>> 24) & 0x0F;
    }

    public static int rgb(int packed) {
        return packed & 0xFFFFFF;
    }

    public static boolean explored(int packed) {
        return category(packed) != NONE;
    }
}
