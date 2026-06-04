package dev.nightbeam.odysseymap.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.nightbeam.odysseymap.client.RuntimeClientState;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import dev.nightbeam.odysseymap.world.Tile;
import dev.nightbeam.odysseymap.world.TileCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinimapTexture {
    private static final String TEXTURE_NAME = "odysseymap_minimap";
    private static final int MAX_FULLSCREEN_DIM = 512;

    private DynamicTexture dynamicTexture;
    private NativeImage image;
    private ResourceLocation textureLocation;
    private int textureWidth;
    private int textureHeight;
    private boolean needsUpload = true;

    public MinimapTexture(TileCache tileCache) {
        // tileCache kept for future extensions
    }

    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public void compose(Minecraft mc, int size) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        compose(mc, size, size, player.getX(), player.getZ(), true, RuntimeClientState.getZoomBlocksPerPixel());
    }

    /**
     * @param texWidth       texture width in pixels
     * @param texHeight      texture height in pixels
     * @param centerWorldX   world X at texture center
     * @param centerWorldZ   world Z at texture center
     * @param applyShapeMask when false, skip circular HUD mask (fullscreen map)
     * @param blocksPerPixel world blocks per map pixel (higher = zoomed out)
     */
    public void compose(Minecraft mc, int texWidth, int texHeight, double centerWorldX, double centerWorldZ,
                        boolean applyShapeMask, int blocksPerPixel) {
        ClientLevel level = mc.level;
        if (level == null || texWidth <= 0 || texHeight <= 0) {
            return;
        }

        int w = Math.min(texWidth, MAX_FULLSCREEN_DIM);
        int h = Math.min(texHeight, MAX_FULLSCREEN_DIM);
        ensureTexture(w, h);
        if (applyShapeMask) {
            MinimapShape.ensureMask(Math.min(w, h));
        }

        int centerX = Mth.floor(centerWorldX);
        int centerZ = Mth.floor(centerWorldZ);
        int stride = Math.max(1, blocksPerPixel);
        int halfW = w / 2;
        int halfH = h / 2;

        var cache = OdysseyMapClient.getTileCache();

        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int wx = centerX + (px - halfW) * stride;
                int wz = centerZ + (py - halfH) * stride;

                int tileAlignedX = TileCache.alignTile(wx);
                int tileAlignedZ = TileCache.alignTile(wz);
                Tile buffer = cache.getOrCreate(level.dimension(), tileAlignedX, tileAlignedZ);
                int dataX = wx - tileAlignedX + 64;
                int dataZ = wz - tileAlignedZ + 64;

                int argb = 0x00000000;
                if (dataX >= 0 && dataZ >= 0 && dataX < Tile.SIZE && dataZ < Tile.SIZE) {
                    argb = buffer.getPixel(dataX, dataZ);
                }
                if (applyShapeMask) {
                    argb = MinimapShape.applyMask(argb, px, py);
                }
                image.setPixelRGBA(px, py, argb);
                needsUpload = true;
            }
        }

        if (needsUpload) {
            dynamicTexture.upload();
            needsUpload = false;
        }
    }

    private void ensureTexture(int width, int height) {
        if (dynamicTexture != null && textureWidth == width && textureHeight == height) {
            return;
        }
        if (dynamicTexture != null) {
            dynamicTexture.close();
        }
        textureWidth = width;
        textureHeight = height;
        image = new NativeImage(width, height, false);
        dynamicTexture = new DynamicTexture(image);
        textureLocation = Minecraft.getInstance().getTextureManager().register(TEXTURE_NAME, dynamicTexture);
    }

    public void clear() {
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        textureLocation = null;
        image = null;
        textureWidth = 0;
        textureHeight = 0;
    }
}
