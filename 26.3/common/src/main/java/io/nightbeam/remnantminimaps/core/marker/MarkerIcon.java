package io.nightbeam.remnantminimaps.core.marker;

import java.util.Locale;

/**
 * Icons in {@code textures/gui/markers.png}, an 8x4 atlas of 64px cells. Tintable icons are drawn
 * in ivory with a dark rim so a tint color recolors them; the others are full color.
 */
public enum MarkerIcon {
    GRACE(0, false, 0xFFFFFF, MarkerGroup.GRACES),
    BEACON(1, false, 0xFFFFFF, MarkerGroup.BEACONS),
    PIN(2, true, 0xE9DFC6, MarkerGroup.LANDMARKS),
    DEATH(3, false, 0xFFFFFF, MarkerGroup.DEATHS),
    BED(4, true, 0xD9A86A, MarkerGroup.REST),
    HOME(5, true, 0xE9DFC6, MarkerGroup.REST),
    VILLAGE(6, true, 0xE9DFC6, MarkerGroup.SETTLEMENTS),
    CASTLE(7, true, 0xE9DFC6, MarkerGroup.SETTLEMENTS),
    CHURCH(8, true, 0xE9DFC6, MarkerGroup.SETTLEMENTS),
    RUINS(9, true, 0xE9DFC6, MarkerGroup.SETTLEMENTS),
    TOWER(10, true, 0xE9DFC6, MarkerGroup.SETTLEMENTS),
    CAVE(11, true, 0xE9DFC6, MarkerGroup.DUNGEONS),
    CATACOMB(12, true, 0xE9DFC6, MarkerGroup.DUNGEONS),
    PORTAL(13, true, 0xC9A7FF, MarkerGroup.DUNGEONS),
    BOSS(14, true, 0xE0705A, MarkerGroup.THREATS),
    DANGER(15, true, 0xE0705A, MarkerGroup.THREATS),
    TREASURE(16, true, 0xE8C35E, MarkerGroup.RESOURCES),
    MINE(17, true, 0xE9DFC6, MarkerGroup.RESOURCES),
    FORGE(18, true, 0xE9DFC6, MarkerGroup.RESOURCES),
    FARM(19, true, 0xD8D07A, MarkerGroup.RESOURCES),
    TREE(20, true, 0xE8C35E, MarkerGroup.LANDMARKS),
    CAMP(21, true, 0xE9A15E, MarkerGroup.REST),
    SHIP(22, true, 0xE9DFC6, MarkerGroup.LANDMARKS),
    STAR(23, true, 0xF2DFA0, MarkerGroup.LANDMARKS),
    FLAG(24, true, 0xE0705A, MarkerGroup.LANDMARKS),
    MERCHANT(25, true, 0xE8C35E, MarkerGroup.RESOURCES),
    GATE(26, true, 0x9FD3E0, MarkerGroup.DUNGEONS),
    SPRING(27, true, 0x9FC9E0, MarkerGroup.RESOURCES),
    MOUNTAIN(28, true, 0xE9DFC6, MarkerGroup.LANDMARKS),
    QUESTION(29, true, 0xE9DFC6, MarkerGroup.LANDMARKS),
    SWORD(30, true, 0xE0705A, MarkerGroup.THREATS),
    EYE(31, true, 0xE9DFC6, MarkerGroup.LANDMARKS);

    public static final int ATLAS_COLUMNS = 8;
    public static final int ATLAS_ROWS = 4;
    public static final int CELL = 64;

    private final int atlasIndex;
    private final boolean tintable;
    private final int defaultColor;
    private final MarkerGroup group;

    MarkerIcon(int atlasIndex, boolean tintable, int defaultColor, MarkerGroup group) {
        this.atlasIndex = atlasIndex;
        this.tintable = tintable;
        this.defaultColor = defaultColor;
        this.group = group;
    }

    public float u0() {
        return (atlasIndex % ATLAS_COLUMNS) / (float) ATLAS_COLUMNS;
    }

    public float v0() {
        return (atlasIndex / ATLAS_COLUMNS) / (float) ATLAS_ROWS;
    }

    public float u1() {
        return u0() + 1.0f / ATLAS_COLUMNS;
    }

    public float v1() {
        return v0() + 1.0f / ATLAS_ROWS;
    }

    public boolean tintable() {
        return tintable;
    }

    public int defaultColor() {
        return defaultColor;
    }

    public MarkerGroup group() {
        return group;
    }

    public String translationKey() {
        return "remnantminimaps.icon." + name().toLowerCase(Locale.ROOT);
    }

    /** Icons the player can pick in the marker editor (automatic ones excluded). */
    public static MarkerIcon[] selectable() {
        return java.util.Arrays.stream(values()).filter(icon -> icon != DEATH && icon != BEACON).toArray(MarkerIcon[]::new);
    }
}
