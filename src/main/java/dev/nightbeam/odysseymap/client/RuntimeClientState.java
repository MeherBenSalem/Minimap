package dev.nightbeam.odysseymap.client;

import dev.nightbeam.odysseymap.config.OdysseyConfig;

/**
 * Runtime overrides for values that change during gameplay without rewriting config files.
 */
public final class RuntimeClientState {
    private static int zoomBlocksPerPixel = -1;

    private RuntimeClientState() {}

    public static int getZoomBlocksPerPixel() {
        if (zoomBlocksPerPixel < 1) {
            zoomBlocksPerPixel = OdysseyConfig.ZOOM_BLOCKS_PER_PIXEL.get();
        }
        return zoomBlocksPerPixel;
    }

    public static void zoomIn() {
        int z = getZoomBlocksPerPixel();
        if (z > 1) {
            zoomBlocksPerPixel = z - 1;
        }
    }

    public static void zoomOut() {
        int z = getZoomBlocksPerPixel();
        if (z < 16) {
            zoomBlocksPerPixel = z + 1;
        }
    }

    public static void resetZoom() {
        zoomBlocksPerPixel = OdysseyConfig.ZOOM_BLOCKS_PER_PIXEL.get();
    }
}
