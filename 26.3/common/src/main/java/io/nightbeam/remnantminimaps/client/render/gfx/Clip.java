package io.nightbeam.remnantminimaps.client.render.gfx;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mirrors vanilla's private scissor stack so our own mesh elements get clipped the same way as
 * text and widgets drawn inside the same region. Always push and pop through this class.
 */
public final class Clip {
    private static final Deque<ScreenRectangle> STACK = new ArrayDeque<>();

    private Clip() {}

    /** Callers must not push an empty area: vanilla rejects zero-sized scissors when rendering. */
    public static void push(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
        ScreenRectangle rect = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformAxisAligned(graphics.pose());
        ScreenRectangle last = STACK.peekLast();
        if (last != null) {
            ScreenRectangle intersection = rect.intersection(last);
            rect = intersection != null ? intersection : ScreenRectangle.empty();
        }
        STACK.addLast(rect);
    }

    public static void pop(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
        STACK.pollLast();
    }

    public static @Nullable ScreenRectangle current() {
        return STACK.peekLast();
    }

    /** Safety net in case a screen threw mid-frame. */
    public static void reset() {
        STACK.clear();
    }
}
