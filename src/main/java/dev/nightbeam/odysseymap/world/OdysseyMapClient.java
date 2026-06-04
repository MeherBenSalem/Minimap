package dev.nightbeam.odysseymap.world;

import dev.nightbeam.odysseymap.config.BlockOverrideConfig;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import dev.nightbeam.odysseymap.marker.MarkerStorage;
import dev.nightbeam.odysseymap.render.MinimapTexture;

public final class OdysseyMapClient {
    private static TileCache tileCache;
    private static WorldScanner scanner;
    private static MinimapTexture minimapTexture;
    private static ColumnSampler columnSampler;

    private OdysseyMapClient() {}

    public static void init() {
        columnSampler = new ColumnSampler();
        tileCache = new TileCache(columnSampler);
        scanner = new WorldScanner(tileCache);
        minimapTexture = new MinimapTexture(tileCache);
        BlockOverrideConfig.reload();
        MarkerStorage.load();
    }

    public static void reset() {
        if (tileCache != null) {
            tileCache.clear();
        }
        if (minimapTexture != null) {
            minimapTexture.clear();
        }
        MarkerManager.get().clearSession();
    }

    public static TileCache getTileCache() {
        return tileCache;
    }

    public static WorldScanner getScanner() {
        return scanner;
    }

    public static MinimapTexture getMinimapTexture() {
        return minimapTexture;
    }

    public static ColumnSampler getColumnSampler() {
        return columnSampler;
    }
}
