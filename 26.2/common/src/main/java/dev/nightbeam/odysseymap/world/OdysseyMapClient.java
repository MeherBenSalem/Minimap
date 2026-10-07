package dev.nightbeam.odysseymap.world;

import dev.nightbeam.odysseymap.config.BlockOverrideConfig;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import dev.nightbeam.odysseymap.marker.MarkerStorage;
import dev.nightbeam.odysseymap.render.MinimapTexture;
import dev.nightbeam.odysseymap.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Path;

public final class OdysseyMapClient {
    private static TileCache tileCache;
    private static WorldScanner scanner;
    private static MinimapTexture minimapTexture;
    private static ColumnSampler columnSampler;
    private static int saveTicks;

    private OdysseyMapClient() {}

    public static void init() {
        columnSampler = new ColumnSampler();
        tileCache = new TileCache(columnSampler);
        scanner = new WorldScanner(tileCache);
        minimapTexture = new MinimapTexture(tileCache);
        BlockOverrideConfig.reload();
        MarkerStorage.load();
    }

    public static boolean tickSession(Minecraft mc) {
        if (tileCache == null) return false;
        // Retry failed writes even at the title screen, with a small per-second budget.
        if (++saveTicks >= 20) {
            saveTicks = 0;
            tileCache.flush(8);
        }
        if (mc.level == null || mc.player == null) return false;
        Path root;
        if (mc.getSingleplayerServer() != null) {
            root = TileStorage.singleplayerDirectory(mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT));
        } else if (mc.getCurrentServer() != null) {
            root = TileStorage.multiplayerDirectory(Services.PLATFORM.getConfigDir(), mc.getCurrentServer().ip);
        } else {
            // Wait for a stable world identity rather than sharing a fallback cache between worlds.
            return false;
        }
        tileCache.setStorageRoot(root);
        tileCache.loadRenderTiles(8);
        return true;
    }

    public static void unloadLevel() {
        if (tileCache != null) tileCache.clear();
        if (scanner != null) scanner.reset();
    }

    public static void reset() {
        if (tileCache != null) tileCache.endSession();
        if (scanner != null) scanner.reset();
        if (minimapTexture != null) minimapTexture.clear();
        MarkerManager.get().clearSession();
    }

    public static TileCache getTileCache() { return tileCache; }
    public static WorldScanner getScanner() { return scanner; }
    public static MinimapTexture getMinimapTexture() { return minimapTexture; }
    public static ColumnSampler getColumnSampler() { return columnSampler; }
}
