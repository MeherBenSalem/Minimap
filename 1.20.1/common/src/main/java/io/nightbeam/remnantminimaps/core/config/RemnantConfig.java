package io.nightbeam.remnantminimaps.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.nightbeam.remnantminimaps.core.style.MapStyle;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, persisted as JSON. Missing fields keep their defaults. */
public final class RemnantConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Shape { CIRCLE, SQUARE }

    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    // Minimap
    public boolean minimapEnabled = true;
    public Shape minimapShape = Shape.CIRCLE;
    public Anchor minimapAnchor = Anchor.TOP_RIGHT;
    public int minimapSize = 112;
    public int minimapMargin = 10;
    public boolean minimapRotate = true;
    /** GUI pixels per block. */
    public float minimapZoom = 1.0f;
    public float minimapOpacity = 1.0f;
    public boolean minimapCoordinates = true;
    public boolean minimapBiome = false;
    public boolean minimapTimeOfDay = true;
    public boolean minimapCardinals = true;
    public float iconScale = 1.0f;

    // Radar
    public boolean radarPlayers = true;
    public boolean radarHostiles = false;
    public boolean radarPassives = false;

    // Markers
    public boolean markersOnMinimap = true;
    public boolean markersAtEdge = true;
    public boolean worldBeams = true;
    public boolean worldLabels = true;
    public int worldBeamDistance = 2048;
    public boolean deathMarkers = true;
    public int deathMarkerLimit = 3;
    public boolean bedMarker = true;

    // World map
    public float worldMapZoom = 0.5f;
    public boolean worldMapLabels = true;
    public boolean worldMapClouds = true;
    public boolean worldMapHints = true;
    public boolean worldMapCompass = true;
    public boolean worldMapTimeDial = true;
    public boolean worldMapGrid = false;
    public boolean pixelatedZoom = true;
    public boolean animations = true;

    // Cartography style
    public MapStyle.Palette palette = MapStyle.Palette.PARCHMENT;
    public boolean contours = true;
    public int contourInterval = 8;
    public float hillshade = 1.0f;
    public boolean waterEngraving = true;
    public boolean paperGrain = true;
    public boolean inkOutlines = true;

    // Performance
    public float scanBudgetMs = 2.0f;
    public int maxLoadedRegions = 128;

    public static final float MIN_MINIMAP_ZOOM = 0.125f;
    public static final float MAX_MINIMAP_ZOOM = 6.0f;

    public MapStyle style() {
        return new MapStyle(palette, contours, contourInterval, hillshade, waterEngraving, paperGrain, inkOutlines);
    }

    public void validate() {
        if (minimapShape == null) minimapShape = Shape.CIRCLE;
        if (minimapAnchor == null) minimapAnchor = Anchor.TOP_RIGHT;
        if (palette == null) palette = MapStyle.Palette.PARCHMENT;
        minimapSize = clamp(minimapSize, 64, 320);
        minimapMargin = clamp(minimapMargin, 0, 64);
        minimapZoom = clamp(minimapZoom, MIN_MINIMAP_ZOOM, MAX_MINIMAP_ZOOM);
        minimapOpacity = clamp(minimapOpacity, 0.2f, 1.0f);
        iconScale = clamp(iconScale, 0.5f, 2.0f);
        worldBeamDistance = clamp(worldBeamDistance, 64, 16384);
        deathMarkerLimit = clamp(deathMarkerLimit, 1, 20);
        worldMapZoom = clamp(worldMapZoom, 0.05f, 8.0f);
        contourInterval = clamp(contourInterval, 2, 64);
        hillshade = clamp(hillshade, 0.0f, 2.0f);
        scanBudgetMs = clamp(scanBudgetMs, 0.25f, 10.0f);
        maxLoadedRegions = clamp(maxLoadedRegions, 16, 1024);
    }

    public static RemnantConfig load(Path file) {
        RemnantConfig config = null;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, RemnantConfig.class);
            } catch (IOException | JsonParseException e) {
                config = null;
            }
        }
        if (config == null) {
            config = new RemnantConfig();
        }
        config.validate();
        return config;
    }

    public void save(Path file) throws IOException {
        validate();
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }
}
