package dev.nightbeam.odysseymap.client;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.gui.FullscreenMapScreen;
import dev.nightbeam.odysseymap.gui.WaypointEditScreen;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import dev.nightbeam.odysseymap.render.FullscreenMapRenderer;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientEvents {
    private static final Logger LOG = LoggerFactory.getLogger("OdysseyMap");
    private static boolean minimapVisible = true;
    private int scanFrameCounter;

    public static boolean isMinimapVisible() {
        return minimapVisible && OdysseyConfig.ENABLED.get();
    }

    public void onClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        scanFrameCounter++;
        if (scanFrameCounter >= OdysseyConfig.effectiveScanInterval()) {
            scanFrameCounter = 0;
            OdysseyMapClient.getScanner().tick(mc);
        }

        if (mc.screen instanceof FullscreenMapScreen screen) {
            FullscreenMapRenderer.tickCompose(mc, screen);
        }

        MarkerManager.get().tick(mc);
    }

    public void onToggleMinimap() {
        minimapVisible = !minimapVisible;
    }

    public void onOpenFullscreen(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            LOG.warn("Cannot open fullscreen map: player or level is null");
            return;
        }
        if (!OdysseyConfig.MAP_FULLSCREEN_ENABLED.get()) {
            return;
        }
        FullscreenMapRenderer.invalidate();
        mc.setScreen(new FullscreenMapScreen());
    }

    public void onZoomIn() {
        RuntimeClientState.zoomIn();
        OdysseyMapClient.getTileCache().markAllDirty();
    }

    public void onZoomOut() {
        RuntimeClientState.zoomOut();
        OdysseyMapClient.getTileCache().markAllDirty();
    }

    public void onCreateWaypoint(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        ResourceKey<Level> dim = player.level().dimension();
        BlockPos pos = player.blockPosition();
        String defaultName = "Waypoint " + (MarkerManager.get().getWaypoints().size() + 1);
        mc.setScreen(WaypointEditScreen.forCreate(null, dim, pos.getX(), pos.getY(), pos.getZ(), defaultName));
    }

    public void onPlayerLogout() {
        OdysseyMapClient.reset();
        MarkerManager.get().clearSession();
    }

    public void onLevelUnload() {
        OdysseyMapClient.getTileCache().clear();
    }

    public void onSleep(LocalPlayer player, BlockPos pos) {
        if (player != null) {
            MarkerManager.get().setBedPoint(
                    player.level().dimension(), pos.getX(), pos.getZ());
        }
    }

    public void onPlayerDeath(LocalPlayer newPlayer) {
        if (newPlayer != null) {
            MarkerManager.get().setDeathPoint(
                    newPlayer.level().dimension(),
                    newPlayer.blockPosition().getX(),
                    newPlayer.blockPosition().getZ());
        }
    }

    public void onBlockChange(Minecraft mc, int x, int z) {
        if (mc.level != null && OdysseyMapClient.getTileCache() != null) {
            OdysseyMapClient.getTileCache().invalidateArea(mc.level, x, z, x, z);
        }
    }
}
