package io.nightbeam.remnantminimaps.core.marker;

import java.util.UUID;

/** A point of interest. Plain mutable fields so Gson can persist it directly. */
public final class Marker {
    public enum Kind {
        /** Placed by the player. */
        USER,
        /** The single golden beacon per dimension, placed from the world map. */
        BEACON,
        /** Where the player died. */
        DEATH,
        /** Last bed the player slept in. */
        BED
    }

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public MarkerIcon icon = MarkerIcon.PIN;
    /** RGB tint, or -1 to use the icon's default. */
    public int color = -1;
    public String dimension = "minecraft:overworld";
    public int x;
    public int y;
    public int z;
    public boolean hasY;
    public Kind kind = Kind.USER;
    public boolean showOnMinimap = true;
    public boolean showBeam;
    public boolean showLabel = true;
    public boolean hidden;
    public long created = System.currentTimeMillis();

    public Marker() {}

    public Marker(String name, MarkerIcon icon, String dimension, int x, int y, int z, boolean hasY) {
        this.name = name;
        this.icon = icon;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasY = hasY;
    }

    public MarkerIcon icon() {
        return icon == null ? MarkerIcon.PIN : icon;
    }

    public Kind kind() {
        return kind == null ? Kind.USER : kind;
    }

    public MarkerGroup group() {
        return icon().group();
    }

    /** Effective RGB tint (no alpha). */
    public int tint() {
        MarkerIcon i = icon();
        if (!i.tintable()) {
            return 0xFFFFFF;
        }
        return color < 0 ? i.defaultColor() : color & 0xFFFFFF;
    }

    /** Graces, beacons and deaths are worth tracking even when far away. */
    public boolean isPinned() {
        Kind k = kind();
        return k == Kind.BEACON || k == Kind.DEATH || icon() == MarkerIcon.GRACE || showBeam;
    }

    public double distanceSq(double px, double pz) {
        double dx = x + 0.5 - px;
        double dz = z + 0.5 - pz;
        return dx * dx + dz * dz;
    }

    public Marker copy() {
        Marker m = new Marker();
        m.id = id;
        m.name = name;
        m.icon = icon;
        m.color = color;
        m.dimension = dimension;
        m.x = x;
        m.y = y;
        m.z = z;
        m.hasY = hasY;
        m.kind = kind;
        m.showOnMinimap = showOnMinimap;
        m.showBeam = showBeam;
        m.showLabel = showLabel;
        m.hidden = hidden;
        m.created = created;
        return m;
    }
}
