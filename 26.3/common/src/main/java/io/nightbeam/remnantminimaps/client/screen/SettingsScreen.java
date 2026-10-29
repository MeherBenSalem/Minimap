package io.nightbeam.remnantminimaps.client.screen;

import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.render.gfx.Clip;
import io.nightbeam.remnantminimaps.client.ui.Ui;
import io.nightbeam.remnantminimaps.client.ui.widget.RButton;
import io.nightbeam.remnantminimaps.client.ui.widget.ROption;
import io.nightbeam.remnantminimaps.core.config.RemnantConfig;
import io.nightbeam.remnantminimaps.core.style.MapStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Settings, laid out like the Elden Ring system menu. Every change applies and saves at once. */
public class SettingsScreen extends Screen {
    private enum Tab { MINIMAP, MARKERS, WORLD_MAP, CARTOGRAPHY, PERFORMANCE }

    private static Tab lastTab = Tab.MINIMAP;
    private static final int TAB_W = 118;
    private static final int ROW_H = 18;

    private final @Nullable Screen parent;
    private final List<ROption> rows = new ArrayList<>();
    private Tab tab = lastTab;
    private int scroll;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private long openedAt;

    private record Opt(String key, Supplier<Component> value, IntConsumer step, Supplier<Float> fraction) {}

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("remnantminimaps.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openedAt = openedAt == 0 ? System.currentTimeMillis() : openedAt;
        if (parent instanceof WorldMapScreen) {
            parent.init(width, height);
        }
        int panelX = Math.max(12, width / 2 - 250);
        int panelW = Math.min(width - 24, 500);
        int top = 44;
        int y = top + 8;
        for (Tab t : Tab.values()) {
            addRenderableWidget(new RButton(panelX + 8, y, TAB_W - 16, 18,
                    Component.translatable("remnantminimaps.settings.tab." + t.name().toLowerCase(Locale.ROOT)),
                    RButton.Style.TAB, b -> switchTab(t)).selected(t == tab));
            y += 20;
        }
        contentX = panelX + TAB_W + 10;
        contentW = panelW - TAB_W - 22;
        contentY = top + 8;
        contentH = height - top - 78;

        rows.clear();
        int ry = contentY - scroll * ROW_H;
        for (Opt opt : options(tab)) {
            ROption row = new ROption(contentX, ry, contentW, ROW_H - 2,
                    Component.translatable("remnantminimaps.option." + opt.key()),
                    Component.translatable("remnantminimaps.option." + opt.key() + ".desc"),
                    opt.value(), delta -> {
                        opt.step().accept(delta);
                        RemnantClient.saveConfig();
                    }, opt.fraction());
            row.visible = ry >= contentY && ry + ROW_H <= contentY + contentH;
            rows.add(row);
            addRenderableWidget(row);
            ry += ROW_H;
        }

        addRenderableWidget(new RButton(width / 2 - 60, height - 30, 120, 18, Component.translatable("gui.done"),
                RButton.Style.FRAMED, b -> onClose()));
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        scroll = 0;
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, rows.size() - contentH / ROW_H);
        int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (parent instanceof WorldMapScreen) {
            parent.extractRenderState(graphics, -1, -1, partialTick);
        }
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Clip.reset();
        Art.ensureLoaded();
        float fade = Math.min(1.0f, (System.currentTimeMillis() - openedAt) / 180.0f);
        g.fillGradient(0, 0, width, height, Ui.alpha(0xF2050403, fade), Ui.alpha(0xFA000000, fade));
        int panelX = Math.max(12, width / 2 - 250);
        int panelW = Math.min(width - 24, 500);

        g.pose().pushMatrix();
        g.pose().translate(panelX, 14);
        g.pose().scale(1.5f, 1.5f);
        g.text(font, title, 0, 0, Ui.IVORY, true);
        g.pose().popMatrix();
        Ui.divider(g, panelX + panelW / 2f, 38, panelW, 0.9f);
        Ui.fill(g, panelX + TAB_W, 48, panelX + TAB_W + 1, height - 70, 0x40C9A961);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        Ui.divider(g, width / 2f, height - 62, panelW, 0.6f);
        Component help = Component.translatable("remnantminimaps.settings.hint");
        for (GuiEventListener child : children()) {
            if (child instanceof ROption row && row.visible && row.isMouseOver(mouseX, mouseY)) {
                help = row.description();
            }
        }
        g.textWithWordWrap(font, help, panelX + 8, height - 54, panelW - 16, Ui.MUTED, true);
    }

    @Override
    public void onClose() {
        RemnantClient.saveConfig();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ option definitions

    private static List<Opt> options(Tab tab) {
        RemnantConfig c = RemnantClient.config();
        List<Opt> o = new ArrayList<>();
        switch (tab) {
            case MINIMAP -> {
                o.add(bool("minimap_enabled", () -> c.minimapEnabled, v -> c.minimapEnabled = v));
                o.add(cycle("minimap_shape", RemnantConfig.Shape.values(), () -> c.minimapShape, v -> c.minimapShape = v));
                o.add(cycle("minimap_anchor", RemnantConfig.Anchor.values(), () -> c.minimapAnchor, v -> c.minimapAnchor = v));
                o.add(range("minimap_size", () -> c.minimapSize, v -> c.minimapSize = v, 64, 320, 16, v -> Integer.toString(v)));
                o.add(steps("minimap_zoom", () -> c.minimapZoom, v -> c.minimapZoom = v,
                        new float[]{0.125f, 0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f, 6.0f}, v -> zoomText(v)));
                o.add(bool("minimap_rotate", () -> c.minimapRotate, v -> c.minimapRotate = v));
                o.add(range("minimap_opacity", () -> Math.round(c.minimapOpacity * 100), v -> c.minimapOpacity = v / 100.0f, 20, 100, 10, v -> v + "%"));
                o.add(range("icon_scale", () -> Math.round(c.iconScale * 100), v -> c.iconScale = v / 100.0f, 50, 200, 10, v -> v + "%"));
                o.add(range("minimap_margin", () -> c.minimapMargin, v -> c.minimapMargin = v, 0, 64, 2, v -> Integer.toString(v)));
                o.add(bool("minimap_cardinals", () -> c.minimapCardinals, v -> c.minimapCardinals = v));
                o.add(bool("minimap_coordinates", () -> c.minimapCoordinates, v -> c.minimapCoordinates = v));
                o.add(bool("minimap_time", () -> c.minimapTimeOfDay, v -> c.minimapTimeOfDay = v));
                o.add(bool("minimap_biome", () -> c.minimapBiome, v -> c.minimapBiome = v));
            }
            case MARKERS -> {
                o.add(bool("markers_minimap", () -> c.markersOnMinimap, v -> c.markersOnMinimap = v));
                o.add(bool("markers_edge", () -> c.markersAtEdge, v -> c.markersAtEdge = v));
                o.add(bool("world_beams", () -> c.worldBeams, v -> c.worldBeams = v));
                o.add(bool("world_labels", () -> c.worldLabels, v -> c.worldLabels = v));
                o.add(steps("beam_distance", () -> (float) c.worldBeamDistance, v -> c.worldBeamDistance = Math.round(v),
                        new float[]{128, 256, 512, 1024, 2048, 4096, 8192, 16384}, v -> Math.round(v) + " m"));
                o.add(bool("death_markers", () -> c.deathMarkers, v -> c.deathMarkers = v));
                o.add(range("death_limit", () -> c.deathMarkerLimit, v -> c.deathMarkerLimit = v, 1, 20, 1, v -> Integer.toString(v)));
                o.add(bool("bed_marker", () -> c.bedMarker, v -> c.bedMarker = v));
                o.add(bool("radar_players", () -> c.radarPlayers, v -> c.radarPlayers = v));
                o.add(bool("radar_hostiles", () -> c.radarHostiles, v -> c.radarHostiles = v));
                o.add(bool("radar_passives", () -> c.radarPassives, v -> c.radarPassives = v));
            }
            case WORLD_MAP -> {
                o.add(bool("map_labels", () -> c.worldMapLabels, v -> c.worldMapLabels = v));
                o.add(bool("map_clouds", () -> c.worldMapClouds, v -> c.worldMapClouds = v));
                o.add(bool("map_hints", () -> c.worldMapHints, v -> c.worldMapHints = v));
                o.add(bool("map_compass", () -> c.worldMapCompass, v -> c.worldMapCompass = v));
                o.add(bool("map_dial", () -> c.worldMapTimeDial, v -> c.worldMapTimeDial = v));
                o.add(bool("map_grid", () -> c.worldMapGrid, v -> c.worldMapGrid = v));
                o.add(bool("pixelated", () -> c.pixelatedZoom, v -> c.pixelatedZoom = v));
                o.add(bool("animations", () -> c.animations, v -> c.animations = v));
            }
            case CARTOGRAPHY -> {
                o.add(cycle("palette", MapStyle.Palette.values(), () -> c.palette, v -> c.palette = v));
                o.add(bool("contours", () -> c.contours, v -> c.contours = v));
                o.add(range("contour_interval", () -> c.contourInterval, v -> c.contourInterval = v, 4, 32, 2, v -> v + " m"));
                o.add(range("hillshade", () -> Math.round(c.hillshade * 100), v -> c.hillshade = v / 100.0f, 0, 200, 20, v -> v + "%"));
                o.add(bool("water_engraving", () -> c.waterEngraving, v -> c.waterEngraving = v));
                o.add(bool("paper_grain", () -> c.paperGrain, v -> c.paperGrain = v));
                o.add(bool("ink_outlines", () -> c.inkOutlines, v -> c.inkOutlines = v));
            }
            case PERFORMANCE -> {
                o.add(steps("scan_budget", () -> c.scanBudgetMs, v -> c.scanBudgetMs = v,
                        new float[]{0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f, 6.0f, 8.0f}, v -> String.format(Locale.ROOT, "%.1f ms", v)));
                o.add(steps("max_regions", () -> (float) c.maxLoadedRegions, v -> c.maxLoadedRegions = Math.round(v),
                        new float[]{32, 64, 96, 128, 192, 256, 384, 512}, v -> Integer.toString(Math.round(v))));
            }
        }
        return o;
    }

    private static String zoomText(float v) {
        return v >= 1.0f ? String.format(Locale.ROOT, "x%.1f", v) : String.format(Locale.ROOT, "1/%d", Math.round(1.0f / v));
    }

    private static Opt bool(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new Opt(key,
                () -> Component.translatable(getter.get() ? "options.on" : "options.off"),
                d -> setter.accept(!getter.get()), null);
    }

    private static <E extends Enum<E>> Opt cycle(String key, E[] values, Supplier<E> getter, Consumer<E> setter) {
        return new Opt(key,
                () -> Component.translatable("remnantminimaps.value." + getter.get().name().toLowerCase(Locale.ROOT)),
                d -> setter.accept(values[Math.floorMod(getter.get().ordinal() + d, values.length)]), null);
    }

    private static Opt range(String key, IntSupplier getter, IntConsumer setter, int min, int max, int step,
                             java.util.function.IntFunction<String> format) {
        return new Opt(key,
                () -> Component.literal(format.apply(getter.getAsInt())),
                d -> {
                    int v = getter.getAsInt() + d * step;
                    if (v > max) v = min;
                    if (v < min) v = max;
                    setter.accept(v);
                },
                () -> (getter.getAsInt() - min) / (float) (max - min));
    }

    private static Opt steps(String key, Supplier<Float> getter, Consumer<Float> setter, float[] values,
                             java.util.function.Function<Float, String> format) {
        return new Opt(key,
                () -> Component.literal(format.apply(getter.get())),
                d -> {
                    float current = getter.get();
                    int best = 0;
                    for (int i = 0; i < values.length; i++) {
                        if (Math.abs(values[i] - current) < Math.abs(values[best] - current)) {
                            best = i;
                        }
                    }
                    setter.accept(values[Math.floorMod(best + d, values.length)]);
                },
                () -> {
                    float current = getter.get();
                    int best = 0;
                    for (int i = 0; i < values.length; i++) {
                        if (Math.abs(values[i] - current) < Math.abs(values[best] - current)) {
                            best = i;
                        }
                    }
                    return values.length <= 1 ? 1.0f : best / (float) (values.length - 1);
                });
    }
}
