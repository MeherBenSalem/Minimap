package io.nightbeam.remnantminimaps.core.importer;

import io.nightbeam.remnantminimaps.core.marker.Marker;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import io.nightbeam.remnantminimaps.core.marker.MarkerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds small synthetic Xaero folders in a temp directory; no real Xaero files are used. */
class WaypointImportTest {
    @TempDir
    Path game;

    private Path world(String root, String name) throws IOException {
        Path world = game.resolve(root).resolve(name);
        Files.createDirectories(world);
        return world;
    }

    private static void write(Path dir, String file, String... lines) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(file), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    @Test
    void findsWorldsInBothStandardFoldersAndMapsDimensions() throws IOException {
        Path current = world("xaero/minimap", "My World");
        write(current.resolve("dim%0"), "mw$default_1.txt", "waypoint:A:A:1:64:1:0:false:0:s");
        write(current.resolve("dim%-1"), "mw$default_1.txt", "waypoint:B:B:2:40:2:0:false:0:s");
        write(current.resolve("dim%mymod$sky_realm"), "mw$default_1.txt", "waypoint:C:C:3:90:3:0:false:0:s");
        Path old = world("XaeroWaypoints", "Multiplayer_play.example.com");
        write(old.resolve("dim%1"), "waypoints.txt", "waypoint:D:D:4:60:4:0:false:0:s");
        Files.createDirectories(game.resolve("xaero/minimap/empty-folder"));
        Files.writeString(game.resolve("xaero/minimap/stray.txt"), "not a world");

        List<XaeroFolders.World> worlds = XaeroFolders.findWorlds(game);
        assertEquals(2, worlds.size(), "folders without dim% folders are ignored");

        List<XaeroFolders.Dimension> dims = XaeroFolders.dimensions(current);
        assertEquals(3, dims.size());
        assertEquals(Optional.of("minecraft:overworld"), XaeroFolders.dimensionId("dim%0"));
        assertEquals(Optional.of("minecraft:the_nether"), XaeroFolders.dimensionId("dim%-1"));
        assertEquals(Optional.of("minecraft:the_end"), XaeroFolders.dimensionId("dim%1"));
        assertEquals(Optional.of("mymod:sky_realm"), XaeroFolders.dimensionId("dim%mymod$sky_realm"));
        assertEquals(Optional.empty(), XaeroFolders.dimensionId("dim%7"));
        assertEquals(Optional.empty(), XaeroFolders.dimensionId("dim%no-namespace"));
        assertEquals(Optional.empty(), XaeroFolders.dimensionId("dim%BAD SPACE$x y"));
        assertEquals(Optional.empty(), XaeroFolders.dimensionId("world"));
    }

    @Test
    void picksTheFolderOfTheCurrentWorld() {
        List<XaeroFolders.World> worlds = List.of(
                new XaeroFolders.World(game.resolve("a"), "Other", 3),
                new XaeroFolders.World(game.resolve("b"), "My World", 2),
                new XaeroFolders.World(game.resolve("c"), "Multiplayer_play.example.com", 1));
        assertEquals(1, XaeroFolders.bestMatch(worlds, "sp_my_world"));
        assertEquals(2, XaeroFolders.bestMatch(worlds, "mp_play.example.com"));
        assertEquals(0, XaeroFolders.bestMatch(worlds, "sp_unknown"));
        assertEquals(0, XaeroFolders.bestMatch(worlds, null));
    }

    @Test
    void dryRunCountsAndSkipsDuplicatesWithoutTouchingAnything() throws IOException {
        Path w = world("xaero/minimap", "w");
        write(w.resolve("dim%0"), "mw$default_1.txt",
                "waypoint:Home:H:120:64:-340:14:false:0:s",
                "waypoint:Home:H:120:64:-340:14:false:0:s",         // same file twice
                "waypoint:Existing:E:5:64:5:0:false:0:s",            // already a marker
                "waypoint:Hidden:X:7:~:8:3:true:0:s",
                "waypoint:gui.xaero_deathpoint:D:9:30:9:12:false:1:s",
                "broken line",
                "waypoint:broken");
        write(w.resolve("dim%-1"), "mw$default_1.txt", "waypoint:Fort:F:10:70:10:4:false:0:s");
        write(w.resolve("dim%42"), "mw$default_1.txt", "waypoint:Lost:L:1:1:1:0:false:0:s");
        write(w.resolve("dim%0"), "mw$other_1.txt", "waypoint:Home:H:120:64:-340:14:false:0:s"); // other multiworld

        Marker existing = new Marker("existing", MarkerIcon.PIN, "minecraft:overworld", 5, 64, 5, true);
        WaypointImport.Plan plan = WaypointImport.plan(w, List.of(existing));

        assertEquals(8, plan.found());
        assertEquals(4, plan.toAdd().size());
        assertEquals(3, plan.alreadyPresent());
        assertEquals(1, plan.unreadableLines(), "only the waypoint: line counts as damaged");
        assertEquals(1, plan.unknownDimensions());
        assertEquals(3, plan.perDimension().get("minecraft:overworld"));
        assertEquals(1, plan.perDimension().get("minecraft:the_nether"));

        Marker home = plan.toAdd().stream().filter(m -> m.name.equals("Home")).findFirst().orElseThrow();
        assertEquals("minecraft:overworld", home.dimension);
        assertEquals(120, home.x);
        assertEquals(64, home.y);
        assertEquals(-340, home.z);
        assertTrue(home.hasY);
        assertEquals(0xFFFF55, home.color, "Xaero color 14 is yellow");
        assertEquals(Marker.Kind.USER, home.kind());

        Marker hidden = plan.toAdd().stream().filter(m -> m.name.equals("Hidden")).findFirst().orElseThrow();
        assertTrue(hidden.hidden);
        assertFalse(hidden.hasY);

        Marker death = plan.toAdd().stream().filter(m -> m.icon() == MarkerIcon.DEATH).findFirst().orElseThrow();
        assertEquals(-1, death.color, "the bloodstain emblem keeps its own colors");
        assertEquals("Deathpoint", death.name, "Xaero's internal name is made readable");
    }

    @Test
    void unreadableFilesAreCountedAndOutOfRangeColorsIgnored() throws IOException {
        Path w = world("xaero/minimap", "w");
        Path dim = w.resolve("dim%0");
        write(dim, "a.txt", "waypoint:A:A:1:64:1:99:false:0:s");
        Files.write(dim.resolve("huge.txt"), new byte[(int) XaeroWaypointFile.MAX_FILE_BYTES + 1]);
        WaypointImport.Plan plan = WaypointImport.plan(w, List.of());
        assertEquals(1, plan.unreadableFiles());
        assertEquals(1, plan.toAdd().size());
        assertEquals(-1, plan.toAdd().get(0).color);
    }

    @Test
    void namelessWaypointsUseTheirInitials() {
        Marker m = WaypointImport.toMarker(new XaeroWaypointFile.Waypoint("", "AB", 1, 2, true, 3, 0, false, 0, ""),
                "minecraft:overworld");
        assertEquals("AB", m.name);
        Marker n = WaypointImport.toMarker(new XaeroWaypointFile.Waypoint("", "", 1, 2, true, 3, 0, false, 0, ""),
                "minecraft:overworld");
        assertEquals("Waypoint", n.name);
    }

    @Test
    void applyOnlyAddsAndBackupCopiesTheOldFile() throws IOException {
        Path worldDir = game.resolve("remnantminimaps/worlds/sp_w");
        Path markersFile = worldDir.resolve("markers.json");
        MarkerStore store = new MarkerStore(markersFile);
        store.add(new Marker("Keep me", MarkerIcon.CASTLE, "minecraft:overworld", 1, 2, 3, true));
        store.saveNow();
        String before = Files.readString(markersFile);

        Path w = world("xaero/minimap", "w");
        write(w.resolve("dim%0"), "mw$default_1.txt", "waypoint:New:N:9:9:9:0:false:0:s");
        WaypointImport.Plan plan = WaypointImport.plan(w, store.all());

        Path backup = WaypointImport.backup(markersFile, Instant.parse("2026-11-11T10:00:00Z"));
        assertNotNull(backup);
        assertEquals("markers.json.bak-20261111-100000", backup.getFileName().toString());
        assertEquals(before, Files.readString(backup));
        Path second = WaypointImport.backup(markersFile, Instant.parse("2026-11-11T10:00:00Z"));
        assertEquals("markers.json.bak-20261111-100000-1", second.getFileName().toString(), "never overwrites a backup");

        assertEquals(1, WaypointImport.apply(plan, store));
        store.saveNow();
        MarkerStore reloaded = new MarkerStore(markersFile);
        reloaded.load();
        assertEquals(2, reloaded.all().size());
        assertTrue(reloaded.all().stream().anyMatch(m -> m.name.equals("Keep me") && m.icon() == MarkerIcon.CASTLE));
        assertTrue(reloaded.all().stream().anyMatch(m -> m.name.equals("New")));

        assertNull(WaypointImport.backup(game.resolve("nothing/markers.json"), Instant.now()), "nothing to back up");
    }
}
