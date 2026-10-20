package io.nightbeam.remnantminimaps.client.ui.widget;

import io.nightbeam.remnantminimaps.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Menu-style button: text that lights up gold over a soft highlight bar. */
public class RButton extends AbstractButton {
    public enum Style { PLAIN, FRAMED, TAB }

    private final Consumer<RButton> action;
    private final Style style;
    private boolean selected;

    public RButton(int x, int y, int width, int height, Component message, Style style, Consumer<RButton> action) {
        super(x, y, width, height, message);
        this.style = style;
        this.action = action;
    }

    public RButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean hot = active && (isHoveredOrFocused() || selected);
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        if (style == Style.FRAMED) {
            Ui.panel(g, x, y, w, h, active ? 0.95f : 0.6f);
        }
        if (hot) {
            Ui.highlight(g, x - 6, y + 1, w + 12, h - 2, style == Style.TAB && selected && !isHoveredOrFocused() ? 0.7f : 1.0f);
        }
        int color = !active ? Ui.FAINT : hot ? Ui.GOLD_BRIGHT : Ui.IVORY;
        var font = Minecraft.getInstance().font;
        if (style == Style.TAB) {
            Ui.text(g, font, getMessage(), x + 8, y + (h - 8) / 2, color);
        } else {
            Ui.centered(g, font, getMessage(), x + w / 2, y + (h - 8) / 2, color);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
