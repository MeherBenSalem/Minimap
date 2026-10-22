package io.nightbeam.remnantminimaps.client.render.gfx;

import com.mojang.blaze3d.platform.NativeImage;
import io.nightbeam.remnantminimaps.RemnantMinimaps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The mod's GUI art, loaded from PNGs with generated mipmaps. */
public final class Art {
    public static GuiTexture markers;
    public static GuiTexture playerArrow;
    public static GuiTexture playerOther;
    public static GuiTexture ring;
    public static GuiTexture square;
    public static GuiTexture vignetteDisc;
    public static GuiTexture vignetteSquare;
    public static GuiTexture compass;
    public static GuiTexture dialFace;
    public static GuiTexture dialFrame;
    public static GuiTexture fog;
    public static GuiTexture panel;
    public static GuiTexture highlight;
    public static GuiTexture divider;
    public static GuiTexture checkbox;
    public static GuiTexture reticle;
    public static GuiTexture glow;
    public static GuiTexture mapIcon;
    public static GuiTexture entityDot;
    public static GuiTexture offscreenArrow;
    public static GuiTexture clouds;

    /** Geometry the code relies on, matching the generated art. */
    public static final float RING_HOLE = 220.0f / 256.0f;
    public static final float SQUARE_HOLE = (476.0f - 36.0f) / 512.0f;
    public static final int PANEL_CORNER = 24;

    private static final List<GuiTexture> ALL = new ArrayList<>();
    private static boolean loaded;

    private Art() {}

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        markers = load("markers");
        playerArrow = load("player_arrow");
        playerOther = load("player_other");
        ring = load("minimap_ring");
        square = load("minimap_square");
        vignetteDisc = load("vignette_disc");
        vignetteSquare = load("vignette_square");
        compass = load("compass_rose");
        dialFace = load("time_dial_face");
        dialFrame = load("time_dial_frame");
        fog = load("fog");
        panel = load("panel");
        highlight = load("highlight");
        divider = load("divider");
        checkbox = load("checkbox");
        reticle = load("reticle");
        glow = load("glow");
        mapIcon = load("map_icon");
        entityDot = load("entity_dot");
        offscreenArrow = load("offscreen_arrow");
        clouds = load("cloud_corner");
    }

    /** Drops every texture so the next frame reloads them (after a resource pack change). */
    public static void invalidate() {
        for (GuiTexture texture : ALL) {
            texture.close();
        }
        ALL.clear();
        loaded = false;
    }

    private static GuiTexture load(String name) {
        ResourceLocation id = RemnantMinimaps.id("textures/gui/" + name + ".png");
        int width = 2;
        int height = 2;
        int[] argb = {0xFFFF00FF, 0xFF000000, 0xFF000000, 0xFFFF00FF};
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                width = image.getWidth();
                height = image.getHeight();
                argb = image.makePixelArray();
            } catch (Exception e) {
                RemnantMinimaps.LOG.warn("Failed to read {}", id, e);
            }
        } else {
            RemnantMinimaps.LOG.warn("Missing texture {}", id);
        }
        bleed(argb, width, height);

        int levels = 1;
        while ((width >> levels) >= 1 && (height >> levels) >= 1 && levels < 10) {
            levels++;
        }
        GuiTexture texture = GuiTexture.create("remnantminimaps:" + name, width, height, levels);
        int w = width;
        int h = height;
        int[] level = argb;
        for (int l = 0; l < levels; l++) {
            texture.upload(l, 0, 0, w, h, level, w);
            if (l + 1 < levels) {
                level = downsample(level, w, h);
                w >>= 1;
                h >>= 1;
            }
        }
        ALL.add(texture);
        return texture;
    }

    private static int[] downsample(int[] src, int w, int h) {
        int dw = w >> 1;
        int dh = h >> 1;
        int[] dst = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int s = (y * 2) * w + x * 2;
                dst[y * dw + x] = io.nightbeam.remnantminimaps.core.style.Mipmaps.average(src[s], src[s + 1], src[s + w], src[s + w + 1]);
            }
        }
        return dst;
    }

    /**
     * Copies color from opaque neighbors into fully transparent pixels so linear filtering does
     * not drag black fringes into the art. Alpha is untouched.
     */
    private static void bleed(int[] argb, int w, int h) {
        boolean[] known = new boolean[argb.length];
        int pending = 0;
        for (int i = 0; i < argb.length; i++) {
            known[i] = (argb[i] >>> 24) != 0;
            if (!known[i]) {
                pending++;
            }
        }
        if (pending == argb.length) {
            return;
        }
        for (int pass = 0; pass < 6 && pending > 0; pass++) {
            boolean[] next = known.clone();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (known[i]) {
                        continue;
                    }
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    int n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = y + dy;
                        if (yy < 0 || yy >= h) {
                            continue;
                        }
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx;
                            if (xx < 0 || xx >= w || (dx == 0 && dy == 0)) {
                                continue;
                            }
                            int j = yy * w + xx;
                            if (known[j]) {
                                int c = argb[j];
                                r += (c >> 16) & 0xFF;
                                g += (c >> 8) & 0xFF;
                                b += c & 0xFF;
                                n++;
                            }
                        }
                    }
                    if (n > 0) {
                        argb[i] = ((r / n) << 16) | ((g / n) << 8) | (b / n);
                        next[i] = true;
                        pending--;
                    }
                }
            }
            known = next;
        }
    }
}
