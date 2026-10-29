package io.nightbeam.remnantminimaps.core.style;

import java.util.Objects;

/**
 * Immutable snapshot of the options that affect how raw terrain becomes map pixels.
 * {@link #stamp()} changes whenever any of them change so cached images can be invalidated.
 */
public record MapStyle(
        Palette palette,
        boolean contours,
        int contourInterval,
        float hillshade,
        boolean waterEngraving,
        boolean paperGrain,
        boolean inkOutlines
) {
    public enum Palette {
        /** Muted parchment cartography in the spirit of the Lands Between. */
        PARCHMENT,
        /** Parchment treatment with more of the original color kept. */
        VIVID,
        /** Vanilla map colors with relief shading only. */
        CLASSIC
    }

    public static final MapStyle DEFAULT = new MapStyle(Palette.PARCHMENT, true, 8, 1.0f, true, true, true);

    public MapStyle {
        Objects.requireNonNull(palette);
        contourInterval = Math.max(2, contourInterval);
        hillshade = Math.max(0.0f, Math.min(2.0f, hillshade));
    }

    public int stamp() {
        return Objects.hash(palette, contours, contourInterval, Math.round(hillshade * 100), waterEngraving, paperGrain, inkOutlines, 3);
    }
}
