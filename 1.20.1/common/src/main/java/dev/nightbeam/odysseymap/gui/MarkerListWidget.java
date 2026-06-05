package dev.nightbeam.odysseymap.gui;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.marker.Marker;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import dev.nightbeam.odysseymap.marker.MarkerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MarkerListWidget extends AbstractWidget {
    private static final long DOUBLE_CLICK_MS = 400;

    private final List<Marker> markers = new ArrayList<>();
    private final Consumer<Marker> onSelect;
    private final Consumer<Marker> onDoubleClick;
    private int scroll;
    private Marker selected;
    private long lastClickTime;
    private int lastClickIndex = -1;

    public MarkerListWidget(int x, int y, int width, int height,
                            Consumer<Marker> onSelect, Consumer<Marker> onDoubleClick) {
        super(x, y, width, height, Component.empty());
        this.onSelect = onSelect;
        this.onDoubleClick = onDoubleClick;
        refresh();
    }

    public void refresh() {
        markers.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            markers.addAll(MarkerManager.get().getWaypointsInDimension(mc.level.dimension()));
        } else {
            markers.addAll(MarkerManager.get().getWaypoints());
        }
        scroll = Math.min(scroll, Math.max(0, markers.size() - 1));
    }

    public Marker getSelected() { return selected; }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = getY();
        int rowH = 14;
        int visible = getHeight() / rowH;
        for (int i = 0; i < visible && i + scroll < markers.size(); i++) {
            Marker m = markers.get(i + scroll);
            int rowY = y + i * rowH;
            boolean hover = mouseX >= getX() && mouseX <= getX() + width && mouseY >= rowY && mouseY < rowY + rowH;
            int color = m == selected ? 0xFF446688 : (hover ? 0xFF333333 : 0xFF222222);
            graphics.fill(getX(), rowY, getX() + width, rowY + rowH, color);
            int swatch = 0xFF000000 | (m.getColor() & 0xFFFFFF);
            graphics.fill(getX() + 2, rowY + 4, getX() + 10, rowY + 10, swatch);
            graphics.drawString(Minecraft.getInstance().font, m.getLabel(), getX() + 14, rowY + 3, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible) return false;
        int rowH = 14;
        int index = (int) ((mouseY - getY()) / rowH) + scroll;
        if (index >= 0 && index < markers.size()) {
            selected = markers.get(index);
            onSelect.accept(selected);
            long now = System.currentTimeMillis();
            if (index == lastClickIndex && now - lastClickTime < DOUBLE_CLICK_MS) {
                onDoubleClick.accept(selected);
                lastClickIndex = -1;
            } else {
                lastClickIndex = index;
                lastClickTime = now;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) scroll = Math.max(0, scroll - 1);
        else if (delta < 0) scroll = Math.min(Math.max(0, markers.size() - 1), scroll + 1);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
