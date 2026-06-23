package dev.nightbeam.odysseymap.marker;

public enum MarkerType {
    PLAYER(0xFF55FF55),
    WAYPOINT(0xFFFFFF55),
    DEATH(0xFFFF5555),
    BED(0xFF5555FF),
    PORTAL(0xFFAA55FF),
    STRUCTURE(0xFFFFAA00),
    ENTITY(0xFFAAAAAA),
    CUSTOM(0xFFFFFFFF);

    public final int defaultColor;

    MarkerType(int defaultColor) {
        this.defaultColor = defaultColor;
    }
}
