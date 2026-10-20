package io.nightbeam.remnantminimaps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.nightbeam.remnantminimaps.RemnantMinimaps;
import io.nightbeam.remnantminimaps.client.render.MinimapHud;
import io.nightbeam.remnantminimaps.client.render.WorldBeacons;
import io.nightbeam.remnantminimaps.client.render.gfx.Clip;
import io.nightbeam.remnantminimaps.client.scan.ChunkScanner;
import io.nightbeam.remnantminimaps.client.scan.ScanScheduler;
import io.nightbeam.remnantminimaps.client.screen.MarkerEditScreen;
import io.nightbeam.remnantminimaps.client.screen.SettingsScreen;
import io.nightbeam.remnantminimaps.client.screen.WorldMapScreen;
import io.nightbeam.remnantminimaps.client.session.DimensionMap;
import io.nightbeam.remnantminimaps.client.session.MapSession;
import io.nightbeam.remnantminimaps.core.config.RemnantConfig;
import io.nightbeam.remnantminimaps.core.marker.Marker;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import io.nightbeam.remnantminimaps.platform.Services;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.file.Path;

/** Client-side entry point shared by both loaders: lifecycle, ticking, input and settings. */
public final class RemnantClient {
    private static RemnantConfig config;
    private static Path configFile;
    private static Path dataRoot;
    private static MapSession session;
    private static final ScanScheduler SCANNER = new ScanScheduler();
    private static boolean minimapHidden;
    private static float zoomTarget = 1.0f;
    private static float zoomCurrent = 1.0f;
    private static long lastFrameNanos;
    private static boolean wasDead;
    private static boolean wasSleeping;
    private static String lastDimension;

    private RemnantClient() {}

    public static void init() {
        configFile = Services.PLATFORM.getConfigDir().resolve(RemnantMinimaps.MOD_ID + ".json");
        dataRoot = Services.PLATFORM.getGameDir().resolve(RemnantMinimaps.MOD_ID);
        config = RemnantConfig.load(configFile);
        zoomTarget = config.minimapZoom;
        zoomCurrent = zoomTarget;
        RemnantMinimaps.LOG.info("{} ready on {}", RemnantMinimaps.MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    public static RemnantConfig config() {
        return config;
    }

    public static void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            RemnantMinimaps.LOG.warn("Could not save settings", e);
        }
        if (session != null && session.current() != null) {
            session.current().setStyle(config.style(), config.maxLoadedRegions);
        }
        zoomTarget = config.minimapZoom;
    }

    public static MapSession session() {
        return session;
    }

    public static DimensionMap currentMap() {
        return session == null ? null : session.current();
    }

    public static String currentDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : MapSession.dimensionId(mc.level.dimension());
    }

    // ------------------------------------------------------------------ lifecycle

    public static void onClientTick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            if (session != null && level == null) {
                closeSession();
            }
            return;
        }
        if (session == null) {
            session = MapSession.open(mc, dataRoot);
            SCANNER.reset();
            RemnantMinimaps.LOG.info("Opened map for {}", session.worldId);
        }
        String dimension = MapSession.dimensionId(level.dimension());
        DimensionMap map = session.dimension(dimension, config);
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            SCANNER.reset();
        }

        handleKeys(mc);
        trackLifeEvents(player, dimension);

        int sliceY = level.dimensionType().hasCeiling() ? Mth.floor(player.getY()) + 1 : ChunkScanner.SURFACE;
        long budget = (long) (config.scanBudgetMs * 1_000_000L);
        SCANNER.tick(level, map, player.chunkPosition().x(), player.chunkPosition().z(), sliceY,
                mc.options.getEffectiveRenderDistance(), budget);
        map.tick(budget, player.getX(), player.getZ());
        session.saveMarkers();
    }

    public static void onChunkLoaded(ClientLevel level, LevelChunk chunk) {
        Minecraft mc = Minecraft.getInstance();
        if (session != null && level == mc.level) {
            SCANNER.onChunkLoaded(chunk);
        }
    }

    public static void onDisconnect() {
        closeSession();
    }

    private static void closeSession() {
        if (session != null) {
            RemnantMinimaps.LOG.info("Saving map for {}", session.worldId);
            session.close();
            session = null;
        }
        SCANNER.reset();
        lastDimension = null;
        wasDead = false;
        wasSleeping = false;
    }

    private static void trackLifeEvents(LocalPlayer player, String dimension) {
        boolean dead = player.isDeadOrDying();
        if (dead && !wasDead && config.deathMarkers) {
            BlockPos pos = player.blockPosition();
            session.markers.addDeath(dimension, pos.getX(), pos.getY(), pos.getZ(),
                    Component.translatable("remnantminimaps.marker.death").getString(), config.deathMarkerLimit);
        }
        wasDead = dead;

        boolean sleeping = player.isSleeping();
        if (sleeping && !wasSleeping && config.bedMarker) {
            player.getSleepingPos().ifPresent(bed -> session.markers.setBed(dimension, bed.getX(), bed.getY(), bed.getZ(),
                    Component.translatable("remnantminimaps.marker.bed").getString()));
        }
        wasSleeping = sleeping;
    }

    // ------------------------------------------------------------------ input

    private static void handleKeys(Minecraft mc) {
        while (Keys.OPEN_MAP.consumeClick()) {
            openWorldMap(mc);
        }
        while (Keys.TOGGLE_MINIMAP.consumeClick()) {
            minimapHidden = !minimapHidden;
        }
        while (Keys.ZOOM_IN.consumeClick()) {
            zoomBy(1.5f);
        }
        while (Keys.ZOOM_OUT.consumeClick()) {
            zoomBy(1.0f / 1.5f);
        }
        while (Keys.ADD_MARKER.consumeClick()) {
            addMarkerAtPlayer(mc);
        }
        while (Keys.TOGGLE_ROTATION.consumeClick()) {
            config.minimapRotate = !config.minimapRotate;
            saveConfig();
        }
        while (Keys.SETTINGS.consumeClick()) {
            mc.setScreen(new SettingsScreen(null));
        }
    }

    public static void openWorldMap(Minecraft mc) {
        if (session != null && mc.player != null) {
            mc.setScreen(new WorldMapScreen());
        }
    }

    public static void addMarkerAtPlayer(Minecraft mc) {
        if (session == null || mc.player == null) {
            return;
        }
        BlockPos pos = mc.player.blockPosition();
        Marker marker = new Marker(Component.translatable("remnantminimaps.marker.default_name",
                session.markers.all().size() + 1).getString(), MarkerIcon.PIN,
                currentDimension(), pos.getX(), pos.getY(), pos.getZ(), true);
        mc.setScreen(new MarkerEditScreen(mc.screen, marker, true));
    }

    private static void zoomBy(float factor) {
        config.minimapZoom = Mth.clamp(config.minimapZoom * factor, RemnantConfig.MIN_MINIMAP_ZOOM, RemnantConfig.MAX_MINIMAP_ZOOM);
        zoomTarget = config.minimapZoom;
        saveConfig();
    }

    public static boolean isMinimapHidden() {
        return minimapHidden;
    }

    /** Animated minimap zoom, eased toward the configured value every frame. */
    public static float animatedZoom() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        if (!config.animations) {
            zoomCurrent = zoomTarget;
        } else {
            float k = 1.0f - (float) Math.exp(-dt * 12.0f);
            zoomCurrent = (float) Math.exp(Math.log(zoomCurrent) + (Math.log(zoomTarget) - Math.log(zoomCurrent)) * k);
        }
        return zoomCurrent;
    }

    // ------------------------------------------------------------------ rendering hooks

    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Clip.reset();
        MinimapHud.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    public static void submitWorld(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState state) {
        WorldBeacons.submit(poseStack, collector, state);
    }
}
