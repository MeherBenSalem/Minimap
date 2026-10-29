package io.nightbeam.remnantminimaps.client.ui;

import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.render.gfx.GuiTexture;
import io.nightbeam.remnantminimaps.client.render.gfx.Mesh;
import io.nightbeam.remnantminimaps.client.render.gfx.Samplers;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Shared look of the mod's interface: palette, panels, highlights, outlined text, icons. */
public final class Ui {
    public static final int GOLD = 0xFFC9A961;
    public static final int GOLD_BRIGHT = 0xFFF2DFA0;
    public static final int GOLD_DEEP = 0xFF8A6B2E;
    public static final int IVORY = 0xFFEDE3CC;
    public static final int MUTED = 0xFFA39A86;
    public static final int FAINT = 0xFF6F6858;
    public static final int INK = 0xFF15120D;

    private Ui() {}

    public static int alpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int rgb(int rgb, float alpha) {
        return (Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f) << 24) | (rgb & 0xFFFFFF);
    }

    /** Whole texture stretched over a rectangle. */
    public static void sprite(GuiGraphicsExtractor g, GuiTexture texture, float x, float y, float w, float h, int color) {
        Mesh.textured(texture, Samplers.smooth()).rect(x, y, x + w, y + h, 0, 0, 1, 1, color).submit(g);
    }

    public static void sprite(GuiGraphicsExtractor g, GuiTexture texture, float x, float y, float w, float h,
                              float u0, float v0, float u1, float v1, int color) {
        Mesh.textured(texture, Samplers.smooth()).rect(x, y, x + w, y + h, u0, v0, u1, v1, color).submit(g);
    }

    /** Texture centered at a point and rotated. */
    public static void spriteRotated(GuiGraphicsExtractor g, GuiTexture texture, float cx, float cy, float w, float h,
                                     float radians, int color) {
        Mesh.textured(texture, Samplers.smooth()).sprite(cx, cy, w / 2, h / 2, radians, 0, 0, 1, 1, color).submit(g);
    }

    /** Dark Elden Ring style panel with a thin gold border (9-slice). */
    public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
        nineSlice(g, Art.panel, x, y, w, h, 8.0f, 0.25f, alpha(0xFFFFFFFF, alpha));
    }

    public static void nineSlice(GuiGraphicsExtractor g, GuiTexture texture, float x, float y, float w, float h,
                                 float corner, float uvCorner, int color) {
        float c = Math.min(corner, Math.min(w, h) / 2);
        float[] xs = {x, x + c, x + w - c, x + w};
        float[] ys = {y, y + c, y + h - c, y + h};
        float[] uv = {0, uvCorner, 1 - uvCorner, 1};
        Mesh mesh = Mesh.textured(texture, Samplers.smooth());
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                if (xs[i + 1] - xs[i] <= 0 || ys[j + 1] - ys[j] <= 0) {
                    continue;
                }
                mesh.rect(xs[i], ys[j], xs[i + 1], ys[j + 1], uv[i], uv[j], uv[i + 1], uv[j + 1], color);
            }
        }
        mesh.submit(g);
    }

    /** Soft selection glow used behind hovered or selected rows. */
    public static void highlight(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
        sprite(g, Art.highlight, x, y, w, h, alpha(0xFFFFFFFF, alpha));
    }

    public static void divider(GuiGraphicsExtractor g, float cx, float y, float w, float alpha) {
        sprite(g, Art.divider, cx - w / 2, y - 3, w, 6, alpha(0xFFFFFFFF, alpha));
    }

    public static void glow(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
        sprite(g, Art.glow, cx - radius, cy - radius, radius * 2, radius * 2, color);
    }

    public static void fill(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        Mesh.colored().rect(x0, y0, x1, y1, color).submit(g);
    }

    /** Marker icon from the atlas, centered at (cx, cy). */
    public static void icon(GuiGraphicsExtractor g, MarkerIcon icon, float cx, float cy, float size, int tintRgb, float alpha) {
        int color = rgb(icon.tintable() ? tintRgb : 0xFFFFFF, alpha);
        float h = size / 2;
        Mesh.textured(Art.markers, Samplers.smooth())
                .rect(cx - h, cy - h, cx + h, cy + h, icon.u0(), icon.v0(), icon.u1(), icon.v1(), color)
                .submit(g);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, x, y, color, true);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        g.text(font, text, x, y, color, true);
    }

    public static void centered(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int color) {
        FormattedCharSequence seq = text.getVisualOrderText();
        g.text(font, seq, cx - font.width(seq) / 2, y, color, true);
    }

    public static void centered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color, true);
    }

    /** Map-label style: light text with a dark one-pixel outline, readable on any terrain. */
    public static void outlined(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, int outline) {
        g.text(font, text, x - 1, y, outline, false);
        g.text(font, text, x + 1, y, outline, false);
        g.text(font, text, x, y - 1, outline, false);
        g.text(font, text, x, y + 1, outline, false);
        g.text(font, text, x, y, color, false);
    }

    public static void outlinedCentered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color, int outline) {
        outlined(g, font, text, cx - font.width(text) / 2, y, color, outline);
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "...";
        int dotsW = font.width(dots);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(out.toString() + text.charAt(i)) + dotsW > maxWidth) {
                break;
            }
            out.append(text.charAt(i));
        }
        return out + dots;
    }
}
