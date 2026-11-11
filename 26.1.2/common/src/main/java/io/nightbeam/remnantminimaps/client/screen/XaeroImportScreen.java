package io.nightbeam.remnantminimaps.client.screen;

import io.nightbeam.remnantminimaps.RemnantMinimaps;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.render.gfx.Clip;
import io.nightbeam.remnantminimaps.client.session.MapSession;
import io.nightbeam.remnantminimaps.client.ui.Ui;
import io.nightbeam.remnantminimaps.client.ui.widget.RButton;
import io.nightbeam.remnantminimaps.client.ui.widget.RField;
import io.nightbeam.remnantminimaps.core.importer.WaypointImport;
import io.nightbeam.remnantminimaps.core.importer.XaeroFolders;
import io.nightbeam.remnantminimaps.platform.Services;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Imports waypoints from Xaero's Minimap into the world the player is in. Shows a dry-run summary
 * first; the import itself backs up {@code markers.json} and only ever adds markers.
 */
public class XaeroImportScreen extends Screen {
    private static final int PANEL_W = 380;
    private static final int PANEL_H = 214;
    private static final long REPLAN_DELAY_MS = 400;

    private final @Nullable Screen parent;
    private final List<XaeroFolders.World> worlds;
    private int worldIndex;
    private RField folderField;
    private RButton importButton;
    private int left;
    private int top;
    private long openedAt;
    private long editedAt;
    private String plannedFor;
    private WaypointImport.Plan plan;
    private final List<Component> summary = new ArrayList<>();
    private boolean done;

    public XaeroImportScreen(@Nullable Screen parent) {
        super(Component.translatable("remnantminimaps.import.title"));
        this.parent = parent;
        this.worlds = XaeroFolders.findWorlds(Services.PLATFORM.getGameDir());
        MapSession session = RemnantClient.session();
        this.worldIndex = XaeroFolders.bestMatch(worlds, session == null ? null : session.worldId);
    }

    @Override
    protected void init() {
        openedAt = openedAt == 0 ? System.currentTimeMillis() : openedAt;
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        int x = left + 16;
        int inner = PANEL_W - 32;

        String initial = folderField != null ? folderField.getValue()
                : worlds.isEmpty() ? "" : worlds.get(worldIndex).folder().toString();
        folderField = new RField(font, x, top + 62, inner - 44, 16, Component.translatable("remnantminimaps.import.folder"));
        folderField.setMaxLength(1024);
        folderField.setValue(initial);
        folderField.setResponder(text -> editedAt = System.currentTimeMillis());
        addRenderableWidget(folderField);
        RButton previous = new RButton(x + inner - 40, top + 62, 18, 16, Component.literal("<"), RButton.Style.FRAMED, b -> cycle(-1));
        RButton next = new RButton(x + inner - 18, top + 62, 18, 16, Component.literal(">"), RButton.Style.FRAMED, b -> cycle(1));
        previous.active = worlds.size() > 1;
        next.active = worlds.size() > 1;
        addRenderableWidget(previous);
        addRenderableWidget(next);

        int by = top + PANEL_H - 28;
        importButton = new RButton(left + PANEL_W / 2 - 124, by, 150, 18, Component.empty(), RButton.Style.FRAMED, b -> runImport());
        addRenderableWidget(importButton);
        addRenderableWidget(new RButton(left + PANEL_W / 2 + 32, by, 92, 18, Component.translatable("gui.cancel"),
                RButton.Style.FRAMED, b -> onClose()));
        replan();
    }

    private void cycle(int delta) {
        if (worlds.isEmpty()) {
            return;
        }
        worldIndex = Math.floorMod(worldIndex + delta, worlds.size());
        folderField.setValue(worlds.get(worldIndex).folder().toString());
        replan();
    }

    @Override
    public void tick() {
        if (editedAt != 0 && System.currentTimeMillis() - editedAt > REPLAN_DELAY_MS) {
            editedAt = 0;
            replan();
        }
    }

    /** Dry run: reads the files and counts what would happen, without changing anything. */
    private void replan() {
        String text = folderField.getValue().strip();
        plannedFor = text;
        plan = null;
        summary.clear();
        MapSession session = RemnantClient.session();
        if (session == null) {
            summary.add(Component.translatable("remnantminimaps.import.no_world"));
        } else if (text.isEmpty()) {
            summary.add(Component.translatable("remnantminimaps.import.none_found"));
        } else {
            Path folder = null;
            try {
                folder = Path.of(text);
            } catch (InvalidPathException e) {
                summary.add(Component.translatable("remnantminimaps.import.not_a_folder"));
            }
            if (folder != null && (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) || XaeroFolders.dimensions(folder).isEmpty())) {
                summary.add(Component.translatable("remnantminimaps.import.not_a_folder"));
            } else if (folder != null) {
                plan = WaypointImport.plan(folder, session.markers.all());
                describe(plan);
            }
        }
        updateButton();
    }

    private void describe(WaypointImport.Plan p) {
        summary.add(Component.translatable("remnantminimaps.import.found", p.found(), p.toAdd().size(), p.alreadyPresent()));
        if (!p.perDimension().isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : p.perDimension().entrySet()) {
                parts.add(dimensionName(e.getKey()) + " " + e.getValue());
            }
            parts.sort(String::compareTo);
            summary.add(Component.literal(String.join("  ·  ", parts)));
        }
        if (p.unreadableLines() > 0 || p.unreadableFiles() > 0) {
            summary.add(Component.translatable("remnantminimaps.import.skipped_damaged", p.unreadableLines(), p.unreadableFiles()));
        }
        if (p.unknownDimensions() > 0) {
            summary.add(Component.translatable("remnantminimaps.import.skipped_dimension", p.unknownDimensions()));
        }
        if (p.overLimit() > 0) {
            summary.add(Component.translatable("remnantminimaps.import.skipped_limit", p.overLimit(), WaypointImport.MAX_IMPORT));
        }
        summary.add(Component.translatable("remnantminimaps.import.backup_note"));
    }

    private static String dimensionName(String id) {
        String key = "remnantminimaps.dimension." + id.replace(':', '.');
        return Language.getInstance().has(key) ? Component.translatable(key).getString() : id;
    }

    private void updateButton() {
        int count = plan == null ? 0 : plan.toAdd().size();
        importButton.setMessage(Component.translatable("remnantminimaps.import.confirm", count));
        importButton.active = !done && count > 0 && RemnantClient.session() != null && folderField.getValue().strip().equals(plannedFor);
    }

    private void runImport() {
        MapSession session = RemnantClient.session();
        if (done || plan == null || plan.isEmpty() || session == null) {
            return;
        }
        Path markersFile = session.worldDir.resolve("markers.json");
        Path backup;
        try {
            backup = WaypointImport.backup(markersFile, Instant.now());
        } catch (IOException e) {
            RemnantMinimaps.LOG.warn("Could not back up {}; nothing was imported", markersFile, e);
            summary.clear();
            summary.add(Component.translatable("remnantminimaps.import.backup_failed"));
            updateButton();
            return;
        }
        done = true;
        int added = WaypointImport.apply(plan, session.markers);
        session.saveMarkers();
        RemnantMinimaps.LOG.info("Imported {} waypoints from Xaero's Minimap folder {} into {} (backup: {})",
                added, plannedFor, session.worldId, backup == null ? "none needed" : backup.getFileName());
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(backup == null
                    ? Component.translatable("remnantminimaps.import.done", added)
                    : Component.translatable("remnantminimaps.import.done_backup", added, backup.getFileName().toString()));
        }
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Clip.reset();
        Art.ensureLoaded();
        float fade = Math.min(1.0f, (System.currentTimeMillis() - openedAt) / 180.0f);
        Ui.fill(g, 0, 0, width, height, Ui.alpha(0xB0000000, fade));
        Ui.panel(g, left, top, PANEL_W, PANEL_H, 0.97f);
        Ui.centered(g, font, title, left + PANEL_W / 2, top + 12, Ui.GOLD_BRIGHT);
        Ui.divider(g, left + PANEL_W / 2f, top + 25, PANEL_W - 60, 0.9f);

        int x = left + 16;
        Ui.text(g, font, Component.translatable("remnantminimaps.import.intro1"), x, top + 32, Ui.MUTED);
        Ui.text(g, font, Component.translatable("remnantminimaps.import.intro2"), x, top + 42, Ui.MUTED);
        Ui.text(g, font, worlds.isEmpty() ? Component.translatable("remnantminimaps.import.folder_none")
                : Component.translatable("remnantminimaps.import.folder_count", worldIndex + 1, worlds.size()), x, top + 52, Ui.GOLD);

        int y = top + 88;
        for (Component line : summary) {
            Ui.text(g, font, Ui.ellipsize(font, line.getString(), PANEL_W - 32), x, y, Ui.IVORY);
            y += 12;
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        minecraft.gui.extractDeferredSubtitles();
    }
}
