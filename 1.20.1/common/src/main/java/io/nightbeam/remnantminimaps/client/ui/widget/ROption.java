package io.nightbeam.remnantminimaps.client.ui.widget;

import io.nightbeam.remnantminimaps.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Settings row in the style of the Elden Ring options menu: label on the left, the value between
 * two arrows on the right. Left click or the right arrow steps forward, right click or the left
 * arrow steps back. Numeric rows also show a segmented bar.
 */
public class ROption extends AbstractButton {
    private static final int VALUE_WIDTH = 150;

    private final Supplier<Component> value;
    private final IntConsumer step;
    private final Supplier<Float> fraction;
    private final Component description;

    public ROption(int x, int y, int width, int height, Component label, Component description,
                   Supplier<Component> value, IntConsumer step, Supplier<Float> fraction) {
        super(x, y, width, height, label);
        this.description = description;
        this.value = value;
        this.step = step;
        this.fraction = fraction;
    }

    public Component description() {
        return description;
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isValidClickButton(button) || !clicked(mouseX, mouseY)) {
            return false;
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        if (button == 1) {
            step.accept(-1);
            return true;
        }
        int valueLeft = getX() + getWidth() - VALUE_WIDTH;
        boolean leftArrow = mouseX >= valueLeft && mouseX < valueLeft + 14;
        step.accept(leftArrow ? -1 : 1);
        return true;
    }

    @Override
    public void onPress() {
        step.accept(1);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean hot = active && isHoveredOrFocused();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int textY = y + (h - 8) / 2;
        if (hot) {
            Ui.highlight(g, x - 8, y, w + 16, h, 1.0f);
        }
        Ui.text(g, font, getMessage(), x + 6, textY, hot ? Ui.GOLD_BRIGHT : Ui.IVORY);

        int valueLeft = x + w - VALUE_WIDTH;
        int valueCenter = valueLeft + VALUE_WIDTH / 2;
        int arrowColor = hot ? Ui.GOLD : Ui.FAINT;
        Ui.text(g, font, "◀", valueLeft + 2, textY, arrowColor);
        Ui.text(g, font, "▶", x + w - 10, textY, arrowColor);
        Float f = fraction == null ? null : fraction.get();
        int valueColor = hot ? Ui.GOLD_BRIGHT : Ui.IVORY;
        if (f != null) {
            int bars = 10;
            int barW = 5;
            int gap = 2;
            int bx = valueLeft + 16;
            int filled = Math.round(f * bars);
            for (int i = 0; i < bars; i++) {
                int color = i < filled ? (hot ? Ui.GOLD_BRIGHT : Ui.GOLD) : 0x40EDE3CC;
                Ui.fill(g, bx + i * (barW + gap), y + h / 2 - 2, bx + i * (barW + gap) + barW, y + h / 2 + 2, color);
            }
            String v = value.get().getString();
            Ui.text(g, font, v, x + w - 14 - font.width(v), textY, valueColor);
        } else {
            Ui.centered(g, font, value.get(), valueCenter, textY, valueColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
