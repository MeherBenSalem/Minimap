package dev.nightbeam.odysseymap.gui;

import dev.nightbeam.odysseymap.marker.Marker;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class MarkerListWidget extends AbstractWidget {
    private static final long DOUBLE_CLICK_MS = 400;
    private static final int ROW_H = 16;
    private static final int STAR_W = 12;
    private static final int EYE_W = 12;
    private static final int SWATCH_W = 10;

    private final List<Marker> allMarkers = new ArrayList<>();
    private final List<Marker> filtered = new ArrayList<>();
    private final Consumer<Marker> onSelect;
    private final Consumer<Marker> onDoubleClick;
    private int scroll;
    private Marker selected;
    private long lastClickTime;
    private int lastClickIndex = -1;
    private String filterText = "";

    public MarkerListWidget(int x, int y, int width, int height,
                            Consumer<Marker> onSelect, Consumer<Marker> onDoubleClick) {
        super(x, y, width, height, Component.empty());
        this.onSelect = onSelect;
        this.onDoubleClick = onDoubleClick;
        refresh();
    }

    public void refresh() {
        allMarkers.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            allMarkers.addAll(MarkerManager.get().getWaypointsInDimension(mc.level.dimension()));
        } else {
            allMarkers.addAll(MarkerManager.get().getWaypoints());
        }
        applyFilter();
    }

    public void setFilter(String text) {
        this.filterText = text != null ? text.toLowerCase() : "";
        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        for (Marker m : allMarkers) {
            if (filterText.isEmpty() || m.getLabel().toLowerCase().contains(filterText)) {
                filtered.add(m);
            }
        }
        // Sort: favorites first, then alphabetical
        filtered.sort(Comparator
                .comparing(Marker::isFavorite).reversed()
                .thenComparing(m -> m.getLabel().toLowerCase()));
        scroll = Math.min(scroll, Math.max(0, filtered.size() - 1));
    }

    public Marker getSelected() { return selected; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int y = getY();
        int visible = getHeight() / ROW_H;
        Minecraft mc = Minecraft.getInstance();

        for (int i = 0; i < visible && i + scroll < filtered.size(); i++) {
            Marker m = filtered.get(i + scroll);
            int rowY = y + i * ROW_H;
            boolean hover = mouseX >= getX() && mouseX <= getX() + width && mouseY >= rowY && mouseY < rowY + ROW_H;

            // Row background
            int bgColor = m == selected ? 0xFF446688 : (hover ? 0xFF333333 : 0xFF222222);
            graphics.fill(getX(), rowY, getX() + width, rowY + ROW_H, bgColor);

            int rowX = getX() + 2;

            // Star (favorite) — click to toggle
            String star = m.isFavorite() ? "\u2605" : "\u2606";
            int starColor = m.isFavorite() ? 0xFFFFD700 : 0xFF666666;
            graphics.text(mc.font, star, rowX, rowY + 3, starColor, false);
            rowX += STAR_W;

            // Eye (visibility) — click to toggle
            String eye = m.isVisible() ? "\u25C9" : "\u25CE";
            int eyeColor = m.isVisible() ? 0xFF88CC88 : 0xFF555555;
            graphics.text(mc.font, eye, rowX, rowY + 3, eyeColor, false);
            rowX += EYE_W;

            // Color swatch
            int swatch = 0xFF000000 | (m.getColor() & 0xFFFFFF);
            graphics.fill(rowX, rowY + 4, rowX + 8, rowY + 12, swatch);
            rowX += SWATCH_W;

            // Label (truncated to fit)
            String label = m.getLabel();
            int maxLabelW = width - (rowX - getX()) - 2;
            while (mc.font.width(label) > maxLabelW && label.length() > 1) {
                label = label.substring(0, label.length() - 1);
            }
            int labelColor = m.isVisible() ? 0xFFFFFF : 0xFF888888;
            graphics.text(mc.font, label, rowX, rowY + 3, labelColor, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        int index = (int) ((mouseY - getY()) / ROW_H) + scroll;
        if (index < 0 || index >= filtered.size()) return false;

        Marker m = filtered.get(index);
        int localX = (int) (mouseX - getX());

        // Star click area
        if (localX >= 2 && localX < 2 + STAR_W) {
            MarkerManager.get().toggleFavorite(m.getId());
            applyFilter();
            return true;
        }

        // Eye click area
        if (localX >= 2 + STAR_W && localX < 2 + STAR_W + EYE_W) {
            MarkerManager.get().toggleVisibility(m.getId());
            refresh();
            return true;
        }

        // Select + double-click
        selected = m;
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) scroll = Math.max(0, scroll - 1);
        else if (scrollY < 0) scroll = Math.min(Math.max(0, filtered.size() - 1), scroll + 1);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
