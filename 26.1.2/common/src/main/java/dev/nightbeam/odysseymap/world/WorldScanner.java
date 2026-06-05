package dev.nightbeam.odysseymap.world;

import dev.nightbeam.odysseymap.client.RuntimeClientState;
import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.gui.FullscreenMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorldScanner {
    private final TileCache tileCache;
    private final Deque<long[]> spiralQueue = new ArrayDeque<>();
    private int spiralRadius;
    private int spiralIndex;
    private ResourceKey<Level> lastDimension;

    public WorldScanner(TileCache tileCache) { this.tileCache = tileCache; }

    public void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        if (lastDimension == null || !level.dimension().equals(lastDimension)) {
            lastDimension = level.dimension();
            tileCache.clear();
            resetSpiral();
        }

        int centerX = Mth.floor(player.getX());
        int centerZ = Mth.floor(player.getZ());
        if (mc.screen instanceof FullscreenMapScreen fullscreen) {
            centerX = Mth.floor(fullscreen.getPanX());
            centerZ = Mth.floor(fullscreen.getPanZ());
        }
        int stride = RuntimeClientState.getZoomBlocksPerPixel();
        int budget = OdysseyConfig.effectiveColumnsPerTick();
        int radius = OdysseyConfig.effectiveScanRadius();

        if (spiralQueue.isEmpty()) buildSpiral(centerX, centerZ, radius, stride);

        int scanned = 0;
        while (scanned < budget && !spiralQueue.isEmpty()) {
            long[] coord = spiralQueue.pollFirst();
            int wx = (int) coord[0];
            int wz = (int) coord[1];
            tileCache.samplePixel(level, wx, wz);
            scanned++;
        }

        if (spiralQueue.isEmpty()) resetSpiral();
    }

    private void resetSpiral() { spiralRadius = 0; spiralIndex = 0; spiralQueue.clear(); }

    private void buildSpiral(int centerX, int centerZ, int radius, int stride) {
        spiralQueue.clear();
        for (int r = 0; r <= radius; r += stride) {
            for (int dx = -r; dx <= r; dx += stride) {
                spiralQueue.add(new long[]{centerX + dx, centerZ - r});
                spiralQueue.add(new long[]{centerX + dx, centerZ + r});
            }
            for (int dz = -r + stride; dz < r; dz += stride) {
                spiralQueue.add(new long[]{centerX - r, centerZ + dz});
                spiralQueue.add(new long[]{centerX + r, centerZ + dz});
            }
        }
    }
}
