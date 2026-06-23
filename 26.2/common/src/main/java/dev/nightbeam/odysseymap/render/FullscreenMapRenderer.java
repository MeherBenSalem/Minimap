package dev.nightbeam.odysseymap.render;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.gui.FullscreenMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates fullscreen map texture composition on the client tick thread.
 * In MC 26.1.2, MinimapTexture.compose() + upload must NOT run inside
 * extractWidgetRenderState (deferred GUI pipeline) — it must run on the
 * client tick thread to avoid render-thread violations and TileCache races.
 */
public final class FullscreenMapRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("OdysseyMap");

    private static double lastPanX = Double.NaN;
    private static double lastPanZ = Double.NaN;
    private static int lastBlocksPerPixel = -1;
    private static int lastW = -1;
    private static int lastH = -1;
    private static int composeThrottle;

    private FullscreenMapRenderer() {}

    /**
     * Called from ClientEvents.onClientTick when the fullscreen map is open.
     * Composes + uploads the minimap texture on the client thread.
     */
    public static void tickCompose(Minecraft mc, FullscreenMapScreen screen) {
        ClientLevel level = mc.level;
        if (level == null) return;

        int bpp = screen.getBlocksPerPixel();
        double panX = screen.getPanX();
        double panZ = screen.getPanZ();

        var panel = screen.getMapPanel();
        if (panel == null) return;
        int w = panel.getWidth();
        int h = panel.getHeight();
        if (w <= 0 || h <= 0) return;

        // Throttle in safe render mode: compose every N ticks
        boolean safeMode = OdysseyConfig.MAP_SAFE_RENDER_MODE.get();
        if (safeMode) {
            composeThrottle++;
            if (composeThrottle < 3) return;
            composeThrottle = 0;
        }

        // Skip recompose if nothing changed
        if (panX == lastPanX && panZ == lastPanZ
                && bpp == lastBlocksPerPixel && w == lastW && h == lastH) {
            return;
        }

        lastPanX = panX;
        lastPanZ = panZ;
        lastBlocksPerPixel = bpp;
        lastW = w;
        lastH = h;

        var texture = dev.nightbeam.odysseymap.world.OdysseyMapClient.getMinimapTexture();
        try {
            texture.compose(mc, w, h, panX, panZ, false, bpp);
        } catch (Exception e) {
            LOG.warn("Fullscreen map compose failed, will retry next tick", e);
        }
    }

    /**
     * Force recompose on next tick (e.g. after dimension change or config toggle).
     */
    public static void invalidate() {
        lastPanX = Double.NaN;
        composeThrottle = 0;
    }
}
