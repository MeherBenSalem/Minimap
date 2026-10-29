package io.nightbeam.remnantminimaps.core.marker;

/** Filter groups shown as checkboxes on the world map, in display order. */
public enum MarkerGroup {
    GRACES,
    BEACONS,
    DEATHS,
    REST,
    SETTLEMENTS,
    DUNGEONS,
    THREATS,
    RESOURCES,
    LANDMARKS;

    public String translationKey() {
        return "remnantminimaps.group." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
