package dev.nightbeam.odysseymap.config;

import com.google.gson.*;
import dev.nightbeam.odysseymap.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OdysseyConfig {
    private static final Logger LOG = LoggerFactory.getLogger("OdysseyMap");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // HUD / minimap
    public static final ConfigValue<Boolean> ENABLED;
    public static final ConfigValue<Integer> MINIMAP_SIZE;
    public static final ConfigValue<ScreenPosition> POSITION;
    public static final ConfigValue<MinimapShape> SHAPE;
    public static final ConfigValue<Integer> ZOOM_BLOCKS_PER_PIXEL;
    public static final ConfigValue<Integer> COLUMNS_PER_TICK;
    public static final ConfigValue<Integer> SCAN_INTERVAL_FRAMES;
    public static final ConfigValue<RotationMode> ROTATION_MODE;
    public static final ConfigValue<Integer> BORDER_COLOR;
    public static final ConfigValue<Integer> BORDER_THICKNESS;
    public static final ConfigValue<Double> TRANSPARENCY;
    public static final ConfigValue<Boolean> SHOW_COMPASS;
    public static final ConfigValue<Boolean> SHOW_COORDINATES;
    public static final ConfigValue<Boolean> HIDE_WHEN_SCREEN_OPEN;
    public static final ConfigValue<PerformanceMode> PERFORMANCE_MODE;

    // Marker toggles
    public static final ConfigValue<Boolean> SHOW_PLAYERS;
    public static final ConfigValue<Boolean> SHOW_WAYPOINTS;
    public static final ConfigValue<Boolean> SHOW_DEATH;
    public static final ConfigValue<Boolean> SHOW_BEDS;
    public static final ConfigValue<Boolean> SHOW_PORTALS;
    public static final ConfigValue<Boolean> SHOW_STRUCTURES;
    public static final ConfigValue<Boolean> SHOW_ENTITIES;
    public static final ConfigValue<Boolean> SHOW_MARKER_DISTANCE;
    public static final ConfigValue<Boolean> STICK_MARKERS_TO_BORDER;
    public static final ConfigValue<Integer> FULLSCREEN_DEFAULT_ZOOM;
    public static final ConfigValue<Boolean> FULLSCREEN_SHOW_GRID;
    public static final ConfigValue<Boolean> SHOW_PLAYER_HEAD;

    // Map config (v1.1.0)
    public static final ConfigValue<Boolean> MAP_FULLSCREEN_ENABLED;
    public static final ConfigValue<Boolean> MAP_SHOW_COORDINATES;
    public static final ConfigValue<Boolean> MAP_SHOW_WAYPOINTS;
    public static final ConfigValue<Boolean> MAP_SHOW_PLAYER_MARKER;
    public static final ConfigValue<Integer> MAP_MAX_WAYPOINTS_RENDERED;
    public static final ConfigValue<Boolean> MAP_SAFE_RENDER_MODE;

    private static final List<ConfigValue<?>> ALL_VALUES = new ArrayList<>();

    static {
        ENABLED = register(new ConfigValue<>("enabled", "Enable the minimap HUD", true));
        MINIMAP_SIZE = register(new ConfigValue<>("minimapSize", "Minimap size in pixels", 128, v -> {
            if (v < 64 || v > 512) throw new IllegalArgumentException("Minimap size must be 64-512");
        }));
        POSITION = register(new ConfigValue<>("position", "Screen corner position", ScreenPosition.TOP_LEFT));
        SHAPE = register(new ConfigValue<>("shape", "Minimap shape", MinimapShape.CIRCLE));
        ZOOM_BLOCKS_PER_PIXEL = register(new ConfigValue<>("zoomBlocksPerPixel", "World blocks per minimap pixel", 1, v -> {
            if (v < 1 || v > 16) throw new IllegalArgumentException("Zoom must be 1-16");
        }));
        ROTATION_MODE = register(new ConfigValue<>("rotationMode", "heading_up or north_up", RotationMode.HEADING_UP));
        BORDER_COLOR = register(new ConfigValue<>("borderColor", "ARGB border color", 0xFF333333));
        BORDER_THICKNESS = register(new ConfigValue<>("borderThickness", "Border thickness in pixels", 2, v -> {
            if (v < 0 || v > 8) throw new IllegalArgumentException("Border thickness must be 0-8");
        }));
        TRANSPARENCY = register(new ConfigValue<>("transparency", "Global HUD alpha 0-1", 0.92, v -> {
            if (v < 0.1 || v > 1.0) throw new IllegalArgumentException("Transparency must be 0.1-1.0");
        }));
        SHOW_COMPASS = register(new ConfigValue<>("showCompass", "Show compass", true));
        SHOW_COORDINATES = register(new ConfigValue<>("showCoordinates", "Show coordinates", true));
        HIDE_WHEN_SCREEN_OPEN = register(new ConfigValue<>("hideWhenScreenOpen", "Hide when any screen is open", true));
        PERFORMANCE_MODE = register(new ConfigValue<>("performanceMode", "Performance level", PerformanceMode.MEDIUM));
        COLUMNS_PER_TICK = register(new ConfigValue<>("columnsPerTick", "Columns sampled per client tick", 256, v -> {
            if (v < 32 || v > 4096) throw new IllegalArgumentException("Must be 32-4096");
        }));
        SCAN_INTERVAL_FRAMES = register(new ConfigValue<>("scanIntervalFrames", "Frames between full scan passes", 1, v -> {
            if (v < 1 || v > 20) throw new IllegalArgumentException("Must be 1-20");
        }));
        SHOW_PLAYERS = register(new ConfigValue<>("showPlayers", "Show player markers", true));
        SHOW_WAYPOINTS = register(new ConfigValue<>("showWaypoints", "Show waypoint markers", true));
        SHOW_DEATH = register(new ConfigValue<>("showDeath", "Show death markers", true));
        SHOW_BEDS = register(new ConfigValue<>("showBeds", "Show bed markers", true));
        SHOW_PORTALS = register(new ConfigValue<>("showPortals", "Show portal markers", true));
        SHOW_STRUCTURES = register(new ConfigValue<>("showStructures", "Show structure markers", false));
        SHOW_ENTITIES = register(new ConfigValue<>("showEntities", "Show entity markers", false));
        SHOW_MARKER_DISTANCE = register(new ConfigValue<>("showMarkerDistance", "Show distance on markers", true));
        STICK_MARKERS_TO_BORDER = register(new ConfigValue<>("stickMarkersToBorder", "Clamp markers to minimap edge", true));
        FULLSCREEN_DEFAULT_ZOOM = register(new ConfigValue<>("fullscreenDefaultZoom", "Default fullscreen zoom", 1, v -> {
            if (v < 1 || v > 16) throw new IllegalArgumentException("Must be 1-16");
        }));
        FULLSCREEN_SHOW_GRID = register(new ConfigValue<>("fullscreenShowGrid", "Show grid on fullscreen map", true));
        SHOW_PLAYER_HEAD = register(new ConfigValue<>("showPlayerHead", "Show 2D player head icon", true));

        // Map config section (v1.1.0)
        MAP_FULLSCREEN_ENABLED = register(new ConfigValue<>("mapFullscreenEnabled", "Allow opening the fullscreen map", true));
        MAP_SHOW_COORDINATES = register(new ConfigValue<>("mapShowCoordinates", "Show coordinates overlay on fullscreen map", true));
        MAP_SHOW_WAYPOINTS = register(new ConfigValue<>("mapShowWaypoints", "Show waypoints on fullscreen map", true));
        MAP_SHOW_PLAYER_MARKER = register(new ConfigValue<>("mapShowPlayerMarker", "Show player marker on fullscreen map", true));
        MAP_MAX_WAYPOINTS_RENDERED = register(new ConfigValue<>("mapMaxWaypointsRendered", "Max waypoints rendered on fullscreen map", 200, v -> {
            if (v < 10 || v > 2000) throw new IllegalArgumentException("Must be 10-2000");
        }));
        MAP_SAFE_RENDER_MODE = register(new ConfigValue<>("mapSafeRenderMode", "Safe render mode: throttled compose, dot markers, fewer labels", true));
    }

    private static <T> ConfigValue<T> register(ConfigValue<T> val) {
        ALL_VALUES.add(val);
        return val;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            for (ConfigValue val : ALL_VALUES) {
                if (root.has(val.getKey())) {
                    JsonElement elem = root.get(val.getKey());
                    if (elem.isJsonPrimitive()) {
                        JsonPrimitive prim = elem.getAsJsonPrimitive();
                        if (val.get() instanceof Boolean && prim.isBoolean()) {
                            val.set(prim.getAsBoolean());
                        } else if (val.get() instanceof Integer && prim.isNumber()) {
                            val.set(prim.getAsInt());
                        } else if (val.get() instanceof Double && prim.isNumber()) {
                            val.set(prim.getAsDouble());
                        } else if (val.get() instanceof String && prim.isString()) {
                            val.set(prim.getAsString());
                        } else if (val.get() instanceof Enum && prim.isString()) {
                            val.setFromObject(prim.getAsString());
                        }
                    }
                }
            }
            // Also read nested "map" object for backward compat
            if (root.has("map") && root.get("map").isJsonObject()) {
                JsonObject map = root.getAsJsonObject("map");
                for (ConfigValue val : ALL_VALUES) {
                    String key = val.getKey();
                    if (map.has(key)) {
                        JsonElement elem = map.get(key);
                        if (elem.isJsonPrimitive()) {
                            JsonPrimitive prim = elem.getAsJsonPrimitive();
                            if (val.get() instanceof Boolean && prim.isBoolean()) {
                                val.set(prim.getAsBoolean());
                            } else if (val.get() instanceof Integer && prim.isNumber()) {
                                val.set(prim.getAsInt());
                            } else if (val.get() instanceof Double && prim.isNumber()) {
                                val.set(prim.getAsDouble());
                            } else if (val.get() instanceof String && prim.isString()) {
                                val.set(prim.getAsString());
                            } else if (val.get() instanceof Enum && prim.isString()) {
                                val.setFromObject(prim.getAsString());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to load config", e);
        }
    }

    public static void save() {
        Path path = getConfigPath();
        Map<String, Object> data = new LinkedHashMap<>();
        for (ConfigValue<?> val : ALL_VALUES) {
            data.put(val.getKey(), val.get());
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOG.error("Failed to save config", e);
        }
    }

    public static void reload() {
        load();
    }

    private static Path getConfigPath() {
        return Services.PLATFORM.getConfigDir().resolve("odysseymap").resolve("config.json");
    }

    public enum ScreenPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public enum MinimapShape {
        CIRCLE, SQUARE
    }

    public enum RotationMode {
        HEADING_UP, NORTH_UP
    }

    public enum PerformanceMode {
        LOW(64, 128, 3),
        MEDIUM(128, 256, 1),
        HIGH(192, 512, 1);

        public final int radiusBlocks;
        public final int columnsPerTick;
        public final int scanInterval;

        PerformanceMode(int radiusBlocks, int columnsPerTick, int scanInterval) {
            this.radiusBlocks = radiusBlocks;
            this.columnsPerTick = columnsPerTick;
            this.scanInterval = scanInterval;
        }
    }

    public static int effectiveColumnsPerTick() {
        PerformanceMode mode = PERFORMANCE_MODE.get();
        return Math.min(COLUMNS_PER_TICK.get(), mode.columnsPerTick);
    }

    public static int effectiveScanRadius() {
        return PERFORMANCE_MODE.get().radiusBlocks;
    }

    public static int effectiveScanInterval() {
        PerformanceMode mode = PERFORMANCE_MODE.get();
        return Math.max(SCAN_INTERVAL_FRAMES.get(), mode.scanInterval);
    }
}
