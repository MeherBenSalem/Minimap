package dev.nightbeam.odysseymap.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class OdysseyConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue MINIMAP_SIZE;
    public static final ForgeConfigSpec.EnumValue<ScreenPosition> POSITION;
    public static final ForgeConfigSpec.EnumValue<MinimapShape> SHAPE;
    public static final ForgeConfigSpec.IntValue ZOOM_BLOCKS_PER_PIXEL;
    public static final ForgeConfigSpec.IntValue COLUMNS_PER_TICK;
    public static final ForgeConfigSpec.IntValue SCAN_INTERVAL_FRAMES;
    public static final ForgeConfigSpec.EnumValue<RotationMode> ROTATION_MODE;
    public static final ForgeConfigSpec.IntValue BORDER_COLOR;
    public static final ForgeConfigSpec.IntValue BORDER_THICKNESS;
    public static final ForgeConfigSpec.DoubleValue TRANSPARENCY;
    public static final ForgeConfigSpec.BooleanValue SHOW_COMPASS;
    public static final ForgeConfigSpec.BooleanValue SHOW_COORDINATES;
    public static final ForgeConfigSpec.BooleanValue HIDE_WHEN_SCREEN_OPEN;
    public static final ForgeConfigSpec.EnumValue<PerformanceMode> PERFORMANCE_MODE;
    public static final ForgeConfigSpec.BooleanValue SHOW_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue SHOW_WAYPOINTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_DEATH;
    public static final ForgeConfigSpec.BooleanValue SHOW_BEDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_PORTALS;
    public static final ForgeConfigSpec.BooleanValue SHOW_STRUCTURES;
    public static final ForgeConfigSpec.BooleanValue SHOW_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue SHOW_MARKER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue STICK_MARKERS_TO_BORDER;
    public static final ForgeConfigSpec.IntValue FULLSCREEN_DEFAULT_ZOOM;
    public static final ForgeConfigSpec.BooleanValue FULLSCREEN_SHOW_GRID;
    public static final ForgeConfigSpec.BooleanValue SHOW_PLAYER_HEAD;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        ENABLED = builder.comment("Enable the minimap HUD").define("enabled", true);
        MINIMAP_SIZE = builder.comment("Minimap size in pixels").defineInRange("minimapSize", 128, 64, 512);
        POSITION = builder.comment("Screen corner position").defineEnum("position", ScreenPosition.TOP_LEFT);
        SHAPE = builder.comment("Minimap shape").defineEnum("shape", MinimapShape.CIRCLE);
        builder.pop();

        builder.push("rendering");
        ZOOM_BLOCKS_PER_PIXEL = builder.comment("World blocks per minimap pixel").defineInRange("zoomBlocksPerPixel", 1, 1, 16);
        ROTATION_MODE = builder.comment("heading_up or north_up").defineEnum("rotationMode", RotationMode.HEADING_UP);
        BORDER_COLOR = builder.comment("ARGB border color").defineInRange("borderColor", 0xFF333333, Integer.MIN_VALUE, Integer.MAX_VALUE);
        BORDER_THICKNESS = builder.comment("Border thickness in pixels").defineInRange("borderThickness", 2, 0, 8);
        TRANSPARENCY = builder.comment("Global HUD alpha 0-1").defineInRange("transparency", 0.92, 0.1, 1.0);
        SHOW_COMPASS = builder.define("showCompass", true);
        SHOW_COORDINATES = builder.define("showCoordinates", true);
        HIDE_WHEN_SCREEN_OPEN = builder.define("hideWhenScreenOpen", true);
        builder.pop();

        builder.push("performance");
        PERFORMANCE_MODE = builder.defineEnum("performanceMode", PerformanceMode.MEDIUM);
        COLUMNS_PER_TICK = builder.comment("Columns sampled per client tick").defineInRange("columnsPerTick", 256, 32, 4096);
        SCAN_INTERVAL_FRAMES = builder.comment("Frames between full scan passes").defineInRange("scanIntervalFrames", 1, 1, 20);
        builder.pop();

        builder.push("markers");
        SHOW_PLAYERS = builder.define("showPlayers", true);
        SHOW_WAYPOINTS = builder.define("showWaypoints", true);
        SHOW_DEATH = builder.define("showDeath", true);
        SHOW_BEDS = builder.define("showBeds", true);
        SHOW_PORTALS = builder.define("showPortals", true);
        SHOW_STRUCTURES = builder.define("showStructures", false);
        SHOW_ENTITIES = builder.define("showEntities", false);
        SHOW_MARKER_DISTANCE = builder.define("showMarkerDistance", true);
        STICK_MARKERS_TO_BORDER = builder.define("stickMarkersToBorder", true);
        builder.pop();

        builder.push("fullscreen");
        FULLSCREEN_DEFAULT_ZOOM = builder.defineInRange("defaultZoom", 1, 1, 16);
        FULLSCREEN_SHOW_GRID = builder.define("showGrid", true);
        builder.pop();

        builder.push("player");
        SHOW_PLAYER_HEAD = builder.comment("Show 2D player head icon instead of a dot").define("showPlayerHead", true);
        builder.pop();

        SPEC = builder.build();
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
