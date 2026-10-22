package io.nightbeam.remnantminimaps.client.render.gfx;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Scissor regions. The GUI draws immediately on this version, so vanilla's own scissor stack
 * already clips our meshes along with text and widgets; this only keeps push and pop balanced.
 */
public final class Clip {
    private static int depth;

    private Clip() {}

    /** Callers must not push an empty area. */
    public static void push(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
        depth++;
    }

    public static void pop(GuiGraphics graphics) {
        if (depth > 0) {
            depth--;
            graphics.disableScissor();
        }
    }

    /** Safety net in case a screen threw mid-frame. */
    public static void reset() {
        depth = 0;
    }
}
