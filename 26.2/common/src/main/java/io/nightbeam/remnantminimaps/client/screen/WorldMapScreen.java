package io.nightbeam.remnantminimaps.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.nightbeam.remnantminimaps.client.Keys;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.TimeOfDay;
import io.nightbeam.remnantminimaps.client.render.MapPainter;
import io.nightbeam.remnantminimaps.client.render.MapView;
import io.nightbeam.remnantminimaps.client.render.MarkerPainter;
import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.render.gfx.Clip;
import io.nightbeam.remnantminimaps.client.render.gfx.Mesh;
import io.nightbeam.remnantminimaps.client.render.gfx.Samplers;
import io.nightbeam.remnantminimaps.client.session.DimensionMap;
import io.nightbeam.remnantminimaps.client.ui.Ui;
import io.nightbeam.remnantminimaps.client.ui.widget.RField;
import io.nightbeam.remnantminimaps.core.config.RemnantConfig;
import io.nightbeam.remnantminimaps.core.map.Columns;
import io.nightbeam.remnantminimaps.core.marker.Marker;
import io.nightbeam.remnantminimaps.core.marker.MarkerGroup;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import io.nightbeam.remnantminimaps.core.marker.MarkerStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * The full world map. Pan by dragging (with inertia) or WASD, zoom toward the cursor with the wheel,
 * click to drop a beacon, right click for the marker menu, G for the list of graces and markers.
 */
public class WorldMapScreen extends Screen {
    private static final EnumSet<MarkerGroup> HIDDEN_GROUPS = EnumSet.noneOf(MarkerGroup.class);
    private static boolean sidebarOpen;

    private static final float MIN_ZOOM = 1.0f / 16.0f;
    private static final float MAX_ZOOM = 8.0f;
    private static final int SIDEBAR_W = 176;
    private static final int TOP_H = 32;
    private static final int BOTTOM_H = 28;
    private static final int ROW_H = 16;

    private final MapPainter painter = new MapPainter();
    private final MapView view = new MapView();

    private boolean viewReady;
    private double centerX;
    private double centerZ;
    private float zoom = 1.0f;
    private float zoomTarget = 1.0f;
    private boolean anchorActive;
    private float anchorSX;
    private float anchorSY;
    private double anchorWX;
    private double anchorWZ;
    private double velX;
    private double velZ;

    private boolean flying;
    private double flyFromX;
    private double flyFromZ;
    private double flyToX;
    private double flyToZ;
    private long flyStart;

    private boolean pressing;
    private boolean dragging;
    private boolean pressDouble;
    private double pressX;
    private double pressY;
    private long lastDragNanos;

    private Marker hovered;
    private ContextMenu menu;
    private RField search;
    private float sidebarAnim;
    private int listScroll;
    private List<Marker> listCache = List.of();
    private int listRevision = -1;
    private String listQuery = "";
    private Marker hoveredRow;

    private long openedAt;
    private long lastFrameNanos;

    public WorldMapScreen() {
        super(Component.translatable("remnantminimaps.map.title"));
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        RemnantConfig cfg = RemnantClient.config();
        if (!viewReady && minecraft.player != null) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
            zoom = Mth.clamp(cfg.worldMapZoom, MIN_ZOOM, MAX_ZOOM);
            zoomTarget = zoom;
            openedAt = System.currentTimeMillis();
            sidebarAnim = sidebarOpen ? 1.0f : 0.0f;
            viewReady = true;
        }
        int sx = width - SIDEBAR_W + 12;
        search = new RField(font, sx, TOP_H + 30, SIDEBAR_W - 24, 15, Component.translatable("remnantminimaps.map.search"));
        search.setHint(Component.translatable("remnantminimaps.map.search").withStyle(s -> s.withColor(0x8A8172)));
        search.setMaxLength(40);
        search.setValue(listQuery);
        search.setResponder(text -> {
            listQuery = text;
            listScroll = 0;
            listRevision = -1;
        });
        search.setVisible(sidebarOpen);
        addRenderableWidget(search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        RemnantConfig cfg = RemnantClient.config();
        cfg.worldMapZoom = zoomTarget;
        RemnantClient.saveConfig();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    private DimensionMap map() {
        return RemnantClient.currentMap();
    }

    private MarkerStore markers() {
        return RemnantClient.session() == null ? null : RemnantClient.session().markers;
    }

    private static boolean groupVisible(Marker m) {
        return !m.hidden && !HIDDEN_GROUPS.contains(m.group());
    }

    private int mapRight() {
        return width - Math.round(SIDEBAR_W * sidebarAnim);
    }

    // ------------------------------------------------------------------ animation

    private void animate(float dt) {
        RemnantConfig cfg = RemnantClient.config();
        boolean animations = cfg.animations;

        if (!search.isFocused()) {
            float speed = 520.0f / zoom * dt;
            double kx = 0;
            double kz = 0;
            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_W) || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_UP)) kz -= 1;
            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_S) || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_DOWN)) kz += 1;
            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_A) || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT)) kx -= 1;
            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_D) || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT)) kx += 1;
            if (kx != 0 || kz != 0) {
                centerX += kx * speed;
                centerZ += kz * speed;
                anchorActive = false;
                flying = false;
                velX = 0;
                velZ = 0;
            }
        }

        if (!animations) {
            zoom = zoomTarget;
        } else {
            float k = 1.0f - (float) Math.exp(-dt * 14.0f);
            zoom = (float) Math.exp(Math.log(zoom) + (Math.log(zoomTarget) - Math.log(zoom)) * k);
            if (Math.abs(Math.log(zoom / zoomTarget)) < 0.0015) {
                zoom = zoomTarget;
            }
        }
        if (anchorActive) {
            centerX = anchorWX - (anchorSX - mapCenterX()) / zoom;
            centerZ = anchorWZ - (anchorSY - height / 2.0f) / zoom;
            if (zoom == zoomTarget) {
                anchorActive = false;
            }
        }

        if (flying) {
            float t = Math.min(1.0f, (System.currentTimeMillis() - flyStart) / 520.0f);
            if (!animations) {
                t = 1.0f;
            }
            float e = t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            centerX = flyFromX + (flyToX - flyFromX) * e;
            centerZ = flyFromZ + (flyToZ - flyFromZ) * e;
            if (t >= 1.0f) {
                flying = false;
            }
        } else if (!dragging && (velX != 0 || velZ != 0)) {
            centerX += velX * dt;
            centerZ += velZ * dt;
            float decay = (float) Math.exp(-dt * 5.5f);
            velX *= decay;
            velZ *= decay;
            if (Math.abs(velX) * zoom < 4 && Math.abs(velZ) * zoom < 4) {
                velX = 0;
                velZ = 0;
            }
        }

        float sidebarTarget = sidebarOpen ? 1.0f : 0.0f;
        sidebarAnim += (sidebarTarget - sidebarAnim) * (animations ? 1.0f - (float) Math.exp(-dt * 16.0f) : 1.0f);
        if (Math.abs(sidebarAnim - sidebarTarget) < 0.002f) {
            sidebarAnim = sidebarTarget;
        }
        search.setVisible(sidebarAnim > 0.98f);
    }

    private float mapCenterX() {
        return mapRight() / 2.0f;
    }

    private void flyTo(double x, double z) {
        flying = true;
        anchorActive = false;
        velX = 0;
        velZ = 0;
        flyFromX = centerX;
        flyFromZ = centerZ;
        flyToX = x;
        flyToZ = z;
        flyStart = System.currentTimeMillis();
    }

    private void zoomAt(float sx, float sy, float factor) {
        view.set(mapCenterX(), height / 2.0f, centerX, centerZ, zoom, 0);
        anchorWX = view.toWorldX(sx, sy);
        anchorWZ = view.toWorldZ(sx, sy);
        anchorSX = sx;
        anchorSY = sy;
        anchorActive = true;
        flying = false;
        zoomTarget = Mth.clamp(zoomTarget * factor, MIN_ZOOM, MAX_ZOOM);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Clip.reset();
        Art.ensureLoaded();
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        animate(dt);

        DimensionMap map = map();
        MarkerStore store = markers();
        LocalPlayer player = minecraft.player;
        if (map == null || store == null || player == null) {
            onClose();
            return;
        }
        RemnantConfig cfg = RemnantClient.config();
        float fade = cfg.animations ? Math.min(1.0f, (System.currentTimeMillis() - openedAt) / 260.0f) : 1.0f;
        int right = mapRight();
        view.set(right / 2.0f, height / 2.0f, centerX, centerZ, zoom, 0);

        // Terrain.
        Ui.fill(g, 0, 0, width, height, 0xFF101010);
        painter.setRectClip(0, 0, right, height);
        painter.drawFog(g, view, 0xFFFFFFFF);
        double guiScale = minecraft.getWindow().getGuiScale();
        double pixelsPerBlock = zoom * guiScale;
        boolean overview = pixelsPerBlock < 0.30;
        boolean crisp = cfg.pixelatedZoom && pixelsPerBlock >= 2.5;
        painter.drawRegions(g, map, view, overview, crisp ? Samplers.crisp() : Samplers.smooth(), 0xFFFFFFFF);
        if (cfg.worldMapGrid) {
            drawGrid(g, right);
        }

        // Atmosphere: vignette and the swirling clouds in the corners.
        Ui.sprite(g, Art.vignetteSquare, 0, 0, right, height, Ui.alpha(0xFFFFFFFF, 0.9f));
        if (cfg.worldMapClouds) {
            drawClouds(g, right);
        }

        // Markers and players.
        String dimension = RemnantClient.currentDimension();
        hovered = null;
        boolean mouseOnMap = mouseX < right && mouseY > TOP_H && mouseY < height - BOTTOM_H && menu == null;
        List<Marker> visible = new ArrayList<>();
        for (Marker m : store.inDimension(dimension)) {
            if (groupVisible(m)) {
                visible.add(m);
            }
        }
        float iconSize = Mth.clamp(11.0f + zoom * 3.0f, 12.0f, 20.0f) * cfg.iconScale;
        if (mouseOnMap) {
            float best = iconSize * 0.7f;
            for (Marker m : visible) {
                float d = (float) Math.hypot(view.toScreenX(m.x + 0.5, m.z + 0.5) - mouseX, view.toScreenY(m.x + 0.5, m.z + 0.5) - mouseY);
                if (d < best) {
                    best = d;
                    hovered = m;
                }
            }
        }
        visible.sort(Comparator.comparingInt(m -> m == hovered ? 1 : 0));
        List<int[]> labelRects = new ArrayList<>();
        for (Marker m : visible) {
            float sx = view.toScreenX(m.x + 0.5, m.z + 0.5);
            float sy = view.toScreenY(m.x + 0.5, m.z + 0.5);
            if (sx < -20 || sy < -20 || sx > right + 20 || sy > height + 20) {
                continue;
            }
            MarkerPainter.draw(g, m, sx, sy, iconSize, 1.0f, m == hovered);
            boolean wantLabel = cfg.worldMapLabels && m.showLabel
                    && (zoom >= 0.4f || m == hovered || m.kind() == Marker.Kind.BEACON || m.icon() == MarkerIcon.GRACE);
            if (wantLabel && !m.name.isEmpty()) {
                String text = Ui.ellipsize(font, m.name, 140);
                int tw = font.width(text);
                int lx = Math.round(sx) - tw / 2;
                int ly = Math.round(sy + iconSize / 2 + 1);
                int[] rect = {lx - 2, ly - 1, lx + tw + 2, ly + 9};
                boolean clear = true;
                if (m != hovered) {
                    for (int[] r : labelRects) {
                        if (rect[0] < r[2] && rect[2] > r[0] && rect[1] < r[3] && rect[3] > r[1]) {
                            clear = false;
                            break;
                        }
                    }
                }
                if (clear) {
                    labelRects.add(rect);
                    int color = m == hovered ? Ui.GOLD_BRIGHT : 0xFFF4EEDF;
                    Ui.outlined(g, font, text, lx, ly, color, 0xE0161210);
                }
            }
        }
        drawPlayers(g, partialTick, right);

        // Chrome.
        drawTopBar(g, map);
        if (cfg.worldMapCompass) {
            Ui.sprite(g, Art.compass, right - 72, 6, 64, 64, Ui.alpha(0xFFFFFFFF, 0.92f));
        }
        if (cfg.worldMapTimeDial && TimeOfDay.hasCycle(minecraft.level)) {
            drawTimeDial(g, right, partialTick);
        }
        drawScaleBar(g, right);
        drawBottomBar(g, mouseX, mouseY, right, mouseOnMap, map);
        drawSidebar(g, mouseX, mouseY, store, dimension);

        if (mouseOnMap && !dragging) {
            Ui.sprite(g, Art.reticle, mouseX - 13, mouseY - 13, 26, 26, Ui.alpha(0xFFFFFFFF, 0.85f));
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        if (menu != null) {
            menu.render(g, mouseX, mouseY);
        } else if (hovered != null) {
            drawMarkerTooltip(g, hovered, mouseX, mouseY, player);
        }

        if (fade < 1.0f) {
            Ui.fill(g, 0, 0, width, height, Ui.alpha(0xFF000000, 1.0f - fade));
        }
    }

    private void drawGrid(GuiGraphicsExtractor g, int right) {
        int step = 256;
        while (step * zoom < 48) {
            step *= 2;
        }
        Mesh lines = Mesh.colored();
        double wx0 = view.toWorldX(0, 0);
        double wx1 = view.toWorldX(right, 0);
        double wz0 = view.toWorldZ(0, 0);
        double wz1 = view.toWorldZ(0, height);
        for (long x = Math.floorDiv((long) Math.floor(wx0), step) * step; x <= wx1; x += step) {
            float sx = view.toScreenX(x, 0);
            lines.rect(sx, 0, sx + 1, height, 0x30201810);
        }
        for (long z = Math.floorDiv((long) Math.floor(wz0), step) * step; z <= wz1; z += step) {
            float sy = view.toScreenY(0, z);
            lines.rect(0, sy, right, sy + 1, 0x30201810);
        }
        lines.submit(g);
    }

    private void drawClouds(GuiGraphicsExtractor g, int right) {
        float s = Math.min(right, height) * 0.42f;
        int color = Ui.alpha(0xFFFFFFFF, 0.88f);
        Mesh mesh = Mesh.textured(Art.clouds, Samplers.smooth());
        mesh.rect(0, 0, s, s, 0, 0, 1, 1, color);
        mesh.rect(right - s, 0, right, s, 1, 0, 0, 1, color);
        mesh.rect(0, height - s, s, height, 0, 1, 1, 0, color);
        mesh.rect(right - s, height - s, right, height, 1, 1, 0, 0, color);
        mesh.submit(g);
    }

    private void drawPlayers(GuiGraphicsExtractor g, float partialTick, int right) {
        LocalPlayer self = minecraft.player;
        RemnantConfig cfg = RemnantClient.config();
        if (cfg.radarPlayers) {
            for (AbstractClientPlayer other : minecraft.level.players()) {
                if (other == self || other.isInvisible()) {
                    continue;
                }
                double ox = Mth.lerp(partialTick, other.xo, other.getX());
                double oz = Mth.lerp(partialTick, other.zo, other.getZ());
                float sx = view.toScreenX(ox, oz);
                float sy = view.toScreenY(ox, oz);
                if (sx < 0 || sy < 0 || sx > right || sy > height) {
                    continue;
                }
                Ui.spriteRotated(g, Art.playerOther, sx, sy, 13, 13, (float) (Math.toRadians(other.getViewYRot(partialTick)) + Math.PI), 0xFFFFFFFF);
                Ui.outlinedCentered(g, font, other.getName().getString(), Math.round(sx), Math.round(sy + 8), 0xFFDCE6F0, 0xD0101418);
            }
        }
        double px = Mth.lerp(partialTick, self.xo, self.getX());
        double pz = Mth.lerp(partialTick, self.zo, self.getZ());
        float sx = view.toScreenX(px, pz);
        float sy = view.toScreenY(px, pz);
        float pulse = 0.8f + 0.2f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        Ui.glow(g, sx, sy, 14 * pulse, Ui.alpha(0xFFFFE6A8, 0.45f));
        Ui.spriteRotated(g, Art.playerArrow, sx, sy, 17, 17, (float) (Math.toRadians(self.getViewYRot(partialTick)) + Math.PI), 0xFFFFFFFF);
    }

    private void drawTopBar(GuiGraphicsExtractor g, DimensionMap map) {
        g.fillGradient(0, 0, width, TOP_H + 14, 0xC0000000, 0x00000000);
        Ui.sprite(g, Art.mapIcon, 8, 6, 20, 20, 0xFFFFFFFF);
        g.pose().pushMatrix();
        float s = crispScale(1.5f);
        g.pose().translate(32, 8);
        g.pose().scale(s, s);
        g.text(font, title, 0, 0, Ui.IVORY, true);
        g.pose().popMatrix();
        Ui.text(g, font, dimensionName(map.dimensionId), 32, 8 + Math.round(9 * s) + 1, Ui.MUTED);
    }

    private Component dimensionName(String id) {
        String key = "remnantminimaps.dimension." + id.replace(':', '.');
        if (net.minecraft.locale.Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String pretty = path.replace('_', ' ').replace('/', ' ');
        return Component.literal(pretty.isEmpty() ? id : Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1));
    }

    private float crispScale(float desired) {
        double gui = minecraft.getWindow().getGuiScale();
        float s = (float) (Math.max(1, Math.round(desired * gui)) / gui);
        return s;
    }

    private void drawTimeDial(GuiGraphicsExtractor g, int right, float partialTick) {
        float size = 58;
        float cx = right - size / 2 - 12;
        float cy = height - BOTTOM_H - size / 2 - 6;
        float angle = TimeOfDay.dialAngle(minecraft.level, partialTick);
        Ui.spriteRotated(g, Art.dialFace, cx, cy, size, size, angle, 0xFFFFFFFF);
        Ui.sprite(g, Art.dialFrame, cx - size / 2, cy - size / 2, size, size, 0xFFFFFFFF);
        Component label = TimeOfDay.label(minecraft.level);
        int tw = font.width(label);
        int lx = Math.round(cx - size / 2 - tw - 14);
        int ly = Math.round(cy - 4);
        Ui.panel(g, lx - 7, ly - 5, tw + 14, 18, 0.9f);
        Ui.text(g, font, label, lx, ly, Ui.IVORY);
    }

    private void drawScaleBar(GuiGraphicsExtractor g, int right) {
        double[] nice = {1, 2, 5};
        double blocks = 1;
        outer:
        for (double pow = 1; pow < 1e6; pow *= 10) {
            for (double n : nice) {
                if (n * pow * zoom >= 50) {
                    blocks = n * pow;
                    break outer;
                }
            }
        }
        float len = (float) (blocks * zoom);
        float x0 = 14;
        float x1 = x0 + len;
        float y = height - BOTTOM_H - 10;
        Mesh bar = Mesh.colored();
        bar.rect(x0 - 1, y - 1, x1 + 1, y + 2, 0xB0000000);
        bar.rect(x0, y, x1, y + 1, Ui.GOLD);
        bar.rect(x0, y - 4, x0 + 1, y + 1, Ui.GOLD);
        bar.rect(x1 - 1, y - 4, x1, y + 1, Ui.GOLD);
        bar.submit(g);
        String text = blocks >= 1000 ? String.format(Locale.ROOT, "%.0f km", blocks / 1000) : String.format(Locale.ROOT, "%.0f m", blocks);
        Ui.outlinedCentered(g, font, text, Math.round((x0 + x1) / 2), Math.round(y - 12), Ui.IVORY, 0xC0000000);
    }

    private void drawBottomBar(GuiGraphicsExtractor g, int mouseX, int mouseY, int right, boolean mouseOnMap, DimensionMap map) {
        g.fillGradient(0, height - BOTTOM_H - 14, right, height, 0x00000000, 0xC8000000);
        RemnantConfig cfg = RemnantClient.config();
        int y = height - 16;
        if (cfg.worldMapHints) {
            int x = 10;
            x = hint(g, x, y, "LMB", "remnantminimaps.hint.beacon");
            x = hint(g, x, y, "RMB", "remnantminimaps.hint.menu");
            x = hint(g, x, y, "G", "remnantminimaps.hint.list");
            x = hint(g, x, y, "↕", "remnantminimaps.hint.zoom");
            x = hint(g, x, y, "Space", "remnantminimaps.hint.center");
            hint(g, x, y, "Esc", "remnantminimaps.hint.back");
        }
        if (mouseOnMap) {
            int wx = Mth.floor(view.toWorldX(mouseX, mouseY));
            int wz = Mth.floor(view.toWorldZ(mouseX, mouseY));
            int packed = map.columnColor(wx, wz);
            String coords = Columns.explored(packed)
                    ? String.format(Locale.ROOT, "X %d   Y %d   Z %d", wx, map.columnHeight(wx, wz), wz)
                    : String.format(Locale.ROOT, "X %d   Z %d", wx, wz);
            Ui.outlinedCentered(g, font, coords, right / 2, height - BOTTOM_H - 12, Ui.IVORY, 0xC0000000);
        }
    }

    private int hint(GuiGraphicsExtractor g, int x, int y, String key, String actionKey) {
        int kw = font.width(key) + 8;
        Ui.fill(g, x, y - 3, x + kw, y + 10, 0xC0000000);
        Mesh border = Mesh.colored();
        border.rect(x, y - 3, x + kw, y - 2, Ui.GOLD_DEEP);
        border.rect(x, y + 9, x + kw, y + 10, Ui.GOLD_DEEP);
        border.rect(x, y - 3, x + 1, y + 10, Ui.GOLD_DEEP);
        border.rect(x + kw - 1, y - 3, x + kw, y + 10, Ui.GOLD_DEEP);
        border.submit(g);
        Ui.text(g, font, key, x + 4, y, Ui.GOLD_BRIGHT);
        Component action = Component.translatable(actionKey);
        Ui.text(g, font, action, x + kw + 4, y, Ui.IVORY);
        return x + kw + 4 + font.width(action) + 12;
    }

    private void drawMarkerTooltip(GuiGraphicsExtractor g, Marker m, int mouseX, int mouseY, LocalPlayer player) {
        String name = m.name.isEmpty() ? Component.translatable(m.icon().translationKey()).getString() : m.name;
        String group = Component.translatable(m.group().translationKey()).getString();
        String where = m.hasY ? String.format(Locale.ROOT, "%d, %d, %d", m.x, m.y, m.z) : String.format(Locale.ROOT, "%d, %d", m.x, m.z);
        String distance = Math.round(Math.sqrt(m.distanceSq(player.getX(), player.getZ()))) + " m";
        int w = Math.max(font.width(name), Math.max(font.width(group), font.width(where + "   " + distance))) + 20;
        int h = 46;
        int x = mouseX + 14;
        int y = mouseY + 10;
        if (x + w > width - 4) x = mouseX - w - 14;
        if (y + h > height - 4) y = mouseY - h - 10;
        Ui.panel(g, x, y, w, h, 0.96f);
        Ui.text(g, font, name, x + 10, y + 8, Ui.GOLD_BRIGHT);
        Ui.text(g, font, group, x + 10, y + 19, Ui.MUTED);
        Ui.text(g, font, where, x + 10, y + 30, Ui.IVORY);
        Ui.text(g, font, distance, x + w - 10 - font.width(distance), y + 30, Ui.IVORY);
    }

    // ------------------------------------------------------------------ sidebar ("grace list")

    private int sidebarX() {
        return mapRight();
    }

    private int filtersY() {
        return TOP_H + 52;
    }

    private int listY() {
        int rows = (MarkerGroup.values().length + 1) / 2;
        return filtersY() + rows * 13 + 12;
    }

    private List<Marker> listEntries(MarkerStore store, String dimension) {
        if (listRevision != store.revision()) {
            listRevision = store.revision();
            String q = listQuery.trim().toLowerCase(Locale.ROOT);
            LocalPlayer p = minecraft.player;
            List<Marker> list = new ArrayList<>();
            for (Marker m : store.inDimension(dimension)) {
                if (HIDDEN_GROUPS.contains(m.group())) {
                    continue;
                }
                if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                list.add(m);
            }
            list.sort(Comparator.<Marker>comparingInt(m -> m.icon() == MarkerIcon.GRACE ? 0 : 1)
                    .thenComparingDouble(m -> m.distanceSq(p.getX(), p.getZ())));
            listCache = list;
        }
        return listCache;
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, MarkerStore store, String dimension) {
        hoveredRow = null;
        if (sidebarAnim <= 0.001f) {
            return;
        }
        int x = sidebarX();
        int top = TOP_H;
        int bottom = height - BOTTOM_H;
        Ui.panel(g, x, top, SIDEBAR_W + 8, bottom - top, 0.97f);
        Ui.centered(g, font, Component.translatable("remnantminimaps.map.list"), x + SIDEBAR_W / 2, top + 10, Ui.GOLD_BRIGHT);
        Ui.divider(g, x + SIDEBAR_W / 2f, top + 23, SIDEBAR_W - 30, 0.9f);

        MarkerGroup[] groups = MarkerGroup.values();
        int colW = (SIDEBAR_W - 24) / 2;
        for (int i = 0; i < groups.length; i++) {
            int gx = x + 12 + (i % 2) * colW;
            int gy = filtersY() + (i / 2) * 13;
            boolean on = !HIDDEN_GROUPS.contains(groups[i]);
            boolean hover = mouseX >= gx && mouseX < gx + colW && mouseY >= gy && mouseY < gy + 12;
            Ui.sprite(g, Art.checkbox, gx, gy + 1, 10, 10, on ? 0.5f : 0.0f, 0.0f, on ? 1.0f : 0.5f, 1.0f, 0xFFFFFFFF);
            String label = Ui.ellipsize(font, Component.translatable(groups[i].translationKey()).getString(), colW - 16);
            Ui.text(g, font, label, gx + 13, gy + 2, hover ? Ui.GOLD_BRIGHT : on ? Ui.IVORY : Ui.FAINT);
        }

        int listTop = listY();
        Ui.divider(g, x + SIDEBAR_W / 2f, listTop - 5, SIDEBAR_W - 30, 0.6f);
        List<Marker> entries = listEntries(store, dimension);
        int visibleRows = Math.max(1, (bottom - 8 - listTop) / ROW_H);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, entries.size() - visibleRows));
        LocalPlayer p = minecraft.player;
        int clipRight = Math.min(width, x + SIDEBAR_W + 4);
        if (clipRight - (x + 4) < 2 || bottom - 6 - listTop < 2) {
            return;
        }
        Clip.push(g, x + 4, listTop, clipRight, bottom - 6);
        for (int i = 0; i < visibleRows && i + listScroll < entries.size(); i++) {
            Marker m = entries.get(i + listScroll);
            int ry = listTop + i * ROW_H;
            boolean hover = mouseX >= x + 4 && mouseX < x + SIDEBAR_W && mouseY >= ry && mouseY < ry + ROW_H && menu == null;
            if (hover) {
                hoveredRow = m;
                Ui.highlight(g, x + 2, ry, SIDEBAR_W, ROW_H, 1.0f);
            }
            MarkerPainter.draw(g, m, x + 18, ry + ROW_H / 2f, 12, m.hidden ? 0.4f : 1.0f, false);
            String dist = Math.round(Math.sqrt(m.distanceSq(p.getX(), p.getZ()))) + "m";
            int dw = font.width(dist);
            String name = m.name.isEmpty() ? Component.translatable(m.icon().translationKey()).getString() : m.name;
            name = Ui.ellipsize(font, name, SIDEBAR_W - 44 - dw);
            Ui.text(g, font, name, x + 28, ry + 4, hover ? Ui.GOLD_BRIGHT : m.hidden ? Ui.FAINT : Ui.IVORY);
            Ui.text(g, font, dist, x + SIDEBAR_W - 6 - dw, ry + 4, Ui.MUTED);
        }
        Clip.pop(g);
        if (entries.isEmpty()) {
            Ui.centered(g, font, Component.translatable("remnantminimaps.map.list_empty"), x + SIDEBAR_W / 2, listTop + 8, Ui.FAINT);
        }
    }

    private boolean sidebarClick(double mx, double my, int button, boolean doubleClick) {
        int x = sidebarX();
        if (sidebarAnim <= 0.001f || mx < x) {
            return false;
        }
        MarkerGroup[] groups = MarkerGroup.values();
        int colW = (SIDEBAR_W - 24) / 2;
        for (int i = 0; i < groups.length; i++) {
            int gx = x + 12 + (i % 2) * colW;
            int gy = filtersY() + (i / 2) * 13;
            if (mx >= gx && mx < gx + colW && my >= gy && my < gy + 12) {
                if (!HIDDEN_GROUPS.remove(groups[i])) {
                    HIDDEN_GROUPS.add(groups[i]);
                }
                listRevision = -1;
                return true;
            }
        }
        if (hoveredRow != null) {
            if (button == 1) {
                openMenu(mx, my, hoveredRow);
            } else if (doubleClick) {
                minecraft.gui.setScreen(new MarkerEditScreen(this, hoveredRow, false));
            } else {
                flyTo(hoveredRow.x + 0.5, hoveredRow.z + 0.5);
                if (zoomTarget < 1.0f) {
                    zoomTarget = 1.0f;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ context menu

    private void openMenu(double mx, double my, Marker target) {
        view.set(mapCenterX(), height / 2.0f, centerX, centerZ, zoom, 0);
        int wx = Mth.floor(view.toWorldX((float) mx, (float) my));
        int wz = Mth.floor(view.toWorldZ((float) mx, (float) my));
        MarkerStore store = markers();
        String dimension = RemnantClient.currentDimension();
        List<MenuItem> items = new ArrayList<>();
        if (target != null) {
            items.add(new MenuItem("remnantminimaps.menu.edit", false, () -> minecraft.gui.setScreen(new MarkerEditScreen(this, target, false))));
            items.add(new MenuItem(target.showBeam ? "remnantminimaps.menu.beam_off" : "remnantminimaps.menu.beam_on", false, () -> {
                target.showBeam = !target.showBeam;
                store.changed();
            }));
            items.add(new MenuItem(target.showOnMinimap ? "remnantminimaps.menu.minimap_off" : "remnantminimaps.menu.minimap_on", false, () -> {
                target.showOnMinimap = !target.showOnMinimap;
                store.changed();
            }));
            items.add(new MenuItem("remnantminimaps.menu.delete", true, () -> store.remove(target)));
        } else {
            items.add(new MenuItem("remnantminimaps.menu.beacon", false, () -> placeBeacon(wx, wz)));
            items.add(new MenuItem("remnantminimaps.menu.add", false, () -> addMarker(wx, wz)));
            if (store.beacon(dimension) != null) {
                items.add(new MenuItem("remnantminimaps.menu.beacon_remove", true, () -> store.clearBeacon(dimension)));
            }
            items.add(new MenuItem("remnantminimaps.menu.center", false, this::centerOnPlayer));
        }
        menu = new ContextMenu((int) mx, (int) my, items);
    }

    private void placeBeacon(int wx, int wz) {
        MarkerStore store = markers();
        store.setBeacon(RemnantClient.currentDimension(), wx, wz, Component.translatable("remnantminimaps.marker.beacon").getString());
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.9f));
    }

    private void addMarker(int wx, int wz) {
        DimensionMap map = map();
        int y = map != null && map.explored(wx, wz) ? map.columnHeight(wx, wz) + 1 : 64;
        Marker marker = new Marker(Component.translatable("remnantminimaps.marker.default_name",
                markers().all().size() + 1).getString(), MarkerIcon.PIN, RemnantClient.currentDimension(),
                wx, y, wz, map != null && map.explored(wx, wz));
        minecraft.gui.setScreen(new MarkerEditScreen(this, marker, true));
    }

    private void centerOnPlayer() {
        if (minecraft.player != null) {
            flyTo(minecraft.player.getX(), minecraft.player.getZ());
        }
    }

    private record MenuItem(String key, boolean danger, Runnable action) {}

    private final class ContextMenu {
        private final List<MenuItem> items;
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        ContextMenu(int mx, int my, List<MenuItem> items) {
            this.items = items;
            int maxW = 0;
            for (MenuItem item : items) {
                maxW = Math.max(maxW, font.width(Component.translatable(item.key())));
            }
            this.w = maxW + 28;
            this.h = items.size() * 15 + 10;
            this.x = Math.min(mx, width - w - 4);
            this.y = Math.min(my, height - h - 4);
        }

        int itemAt(double mx, double my) {
            if (mx < x || mx >= x + w || my < y + 5 || my >= y + h - 5) {
                return -1;
            }
            int i = (int) ((my - y - 5) / 15);
            return i >= 0 && i < items.size() ? i : -1;
        }

        void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            Ui.panel(g, x, y, w, h, 0.98f);
            int hoveredItem = itemAt(mouseX, mouseY);
            for (int i = 0; i < items.size(); i++) {
                int ry = y + 5 + i * 15;
                if (i == hoveredItem) {
                    Ui.highlight(g, x + 2, ry, w - 4, 15, 1.0f);
                }
                MenuItem item = items.get(i);
                int color = i == hoveredItem ? Ui.GOLD_BRIGHT : item.danger() ? 0xFFE08A78 : Ui.IVORY;
                Ui.text(g, font, Component.translatable(item.key()), x + 14, ry + 4, color);
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (menu != null) {
            int i = menu.itemAt(mx, my);
            ContextMenu open = menu;
            menu = null;
            if (i >= 0) {
                open.items.get(i).action().run();
            }
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (search.isFocused()) {
            search.setFocused(false);
            setFocused(null);
        }
        if (sidebarClick(mx, my, event.button(), doubleClick)) {
            return true;
        }
        if (my < TOP_H || my > height - BOTTOM_H) {
            return true;
        }
        if (event.button() == 1) {
            openMenu(mx, my, hovered);
            return true;
        }
        if (event.button() == 0) {
            pressing = true;
            dragging = false;
            pressDouble = doubleClick;
            pressX = mx;
            pressY = my;
            velX = 0;
            velZ = 0;
            flying = false;
            lastDragNanos = System.nanoTime();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressing && event.button() == 0) {
            if (!dragging && Math.hypot(event.x() - pressX, event.y() - pressY) > 3) {
                dragging = true;
            }
            if (dragging) {
                double bx = dx / zoom;
                double bz = dy / zoom;
                centerX -= bx;
                centerZ -= bz;
                anchorActive = false;
                long now = System.nanoTime();
                double dt = Math.max(1.0e-3, (now - lastDragNanos) / 1.0e9);
                lastDragNanos = now;
                velX = velX * 0.6 + (-bx / dt) * 0.4;
                velZ = velZ * 0.6 + (-bz / dt) * 0.4;
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressing && event.button() == 0) {
            pressing = false;
            if (dragging) {
                dragging = false;
                if ((System.nanoTime() - lastDragNanos) / 1.0e9 > 0.08) {
                    velX = 0;
                    velZ = 0;
                }
                return true;
            }
            onMapClick(event.x(), event.y(), pressDouble);
            return true;
        }
        return super.mouseReleased(event);
    }

    private void onMapClick(double mx, double my, boolean doubleClick) {
        MarkerStore store = markers();
        if (store == null) {
            return;
        }
        if (hovered != null) {
            if (hovered.kind() == Marker.Kind.BEACON) {
                store.clearBeacon(RemnantClient.currentDimension());
            } else if (doubleClick) {
                minecraft.gui.setScreen(new MarkerEditScreen(this, hovered, false));
            } else {
                flyTo(hovered.x + 0.5, hovered.z + 0.5);
            }
            return;
        }
        view.set(mapCenterX(), height / 2.0f, centerX, centerZ, zoom, 0);
        placeBeacon(Mth.floor(view.toWorldX((float) mx, (float) my)), Mth.floor(view.toWorldZ((float) mx, (float) my)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sidebarAnim > 0.5f && mouseX >= sidebarX()) {
            listScroll -= (int) Math.signum(scrollY) * 2;
            return true;
        }
        if (scrollY != 0) {
            zoomAt((float) mouseX, (float) mouseY, (float) Math.pow(1.3, scrollY));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search.isFocused()) {
            if (event.isEscape()) {
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            return super.keyPressed(event);
        }
        if (menu != null && event.isEscape()) {
            menu = null;
            return true;
        }
        if (Keys.OPEN_MAP.matches(event)) {
            onClose();
            return true;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_G -> {
                sidebarOpen = !sidebarOpen;
                return true;
            }
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_C -> {
                centerOnPlayer();
                return true;
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                zoomAt(mapCenterX(), height / 2.0f, 1.5f);
                return true;
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                zoomAt(mapCenterX(), height / 2.0f, 1.0f / 1.5f);
                return true;
            }
            case GLFW.GLFW_KEY_H -> {
                RemnantConfig cfg = RemnantClient.config();
                cfg.worldMapHints = !cfg.worldMapHints;
                RemnantClient.saveConfig();
                return true;
            }
            case GLFW.GLFW_KEY_O -> {
                minecraft.gui.setScreen(new SettingsScreen(this));
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(event);
    }
}
