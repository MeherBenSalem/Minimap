package dev.nightbeam.odysseymap.render;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.gui.FullscreenMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FullscreenMapRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("OdysseyMap");

    private static double lastPanX = Double.NaN;
    private static double lastPanZ = Double.NaN;
    private static int lastBlocksPerPixel = -1;
    private static int lastW = -1;
    private static int lastH = -1;
    private static int composeThrottle;

    private FullscreenMapRenderer() {}

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

        boolean safeMode = OdysseyConfig.MAP_SAFE_RENDER_MODE.get();
        if (safeMode) {
            composeThrottle++;
            if (composeThrottle < 3) return;
            composeThrottle = 0;
        }

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

    public static void invalidate() {
        lastPanX = Double.NaN;
        composeThrottle = 0;
    }
}
