package io.nightbeam.remnantminimaps.core.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** All samples are synthetic, written for these tests. */
class XaeroWaypointFileTest {
    @TempDir
    Path dir;

    private static final String SAMPLE = String.join("\n",
            "#",
            "#waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination",
            "#",
            "sets:gui.xaero_default:Bases",
            "waypoint:Home:H:120:64:-340:0:false:0:gui.xaero_default:false:0:0:false",
            "waypoint:Old Mine:M:-5000:12:7000:6:true:0:Bases:false:0",
            "waypoint:gui.xaero_deathpoint:D:10:70:10:12:false:1:gui.xaero_default:false:0:0:false",
            "");

    @Test
    void readsTheDocumentedFields() {
        XaeroWaypointFile.Result result = XaeroWaypointFile.parse(SAMPLE);
        assertEquals(0, result.skipped());
        assertEquals(3, result.waypoints().size());
        XaeroWaypointFile.Waypoint home = result.waypoints().get(0);
        assertEquals("Home", home.name());
        assertEquals("H", home.initials());
        assertEquals(120, home.x());
        assertEquals(64, home.y());
        assertTrue(home.hasY());
        assertEquals(-340, home.z());
        assertEquals(0, home.color());
        assertFalse(home.disabled());
        assertEquals("gui.xaero_default", home.set());

        XaeroWaypointFile.Waypoint mine = result.waypoints().get(1);
        assertTrue(mine.disabled());
        assertEquals(6, mine.color());
        assertEquals("Bases", mine.set());

        assertTrue(result.waypoints().get(2).isDeathpoint());
    }

    @Test
    void handlesEscapedColonsUnknownHeightAndOlderShorterLines() {
        XaeroWaypointFile.Result result = XaeroWaypointFile.parse(
                "waypoint:Gate§§North:G:1:~:2:9:false:0\r\n");
        assertEquals(1, result.waypoints().size());
        XaeroWaypointFile.Waypoint w = result.waypoints().get(0);
        assertEquals("Gate:North", w.name());
        assertFalse(w.hasY());
        assertEquals("", w.set());
    }

    @Test
    void stripsFormattingCodesControlCharactersAndCutsLongNames() {
        String longName = "x".repeat(200);
        XaeroWaypointFile.Result result = XaeroWaypointFile.parse(
                "waypoint:§cRed\u0007 Base:R:0:64:0:12:false:0:s\n"
                        + "waypoint:" + longName + ":L:0:64:0:0:false:0:s\n");
        assertEquals("Red Base", result.waypoints().get(0).name());
        assertEquals(XaeroWaypointFile.MAX_NAME_LENGTH, result.waypoints().get(1).name().length());
    }

    @Test
    void damagedLinesAreCountedNotFatal() {
        String text = String.join("\n",
                "waypoint:Ok:O:1:2:3:4:false:0:s",
                "waypoint:short:S:1:2",
                "waypoint:BadX:B:abc:2:3:4:false:0:s",
                "waypoint:Far:F:40000000:64:0:4:false:0:s",
                "waypoint:Deep:D:0:-99999:0:4:false:0:s",
                "waypoint:BadFlag:B:0:64:0:4:maybe:0:s",
                "something else entirely",
                "waypoint:Ok2:O:5:6:7:99:false:7:s");
        XaeroWaypointFile.Result result = XaeroWaypointFile.parse(text);
        assertEquals(2, result.waypoints().size());
        assertEquals(5, result.skipped());
        assertEquals(99, result.waypoints().get(1).color(), "out-of-range colors are kept here and ignored later");
    }

    @Test
    void parseLineRejectsWrongPrefix() {
        assertNull(XaeroWaypointFile.parseLine("waypoints:a:b:1:2:3:4:false:0"));
        assertNotNull(XaeroWaypointFile.parseLine("waypoint:a:b:1:2:3:4:false:0"));
    }

    @Test
    void readsFilesWithBomAndFallsBackForBadUtf8() throws IOException {
        Path bom = dir.resolve("bom.txt");
        Files.writeString(bom, "﻿waypoint:Börk:B:1:2:3:4:false:0:s\n", StandardCharsets.UTF_8);
        assertEquals("Börk", XaeroWaypointFile.read(bom).waypoints().get(0).name());

        Path latin = dir.resolve("latin.txt");
        byte[] bytes = "waypoint:Café:C:1:2:3:4:false:0:s\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(latin, bytes);
        assertEquals("Café", XaeroWaypointFile.read(latin).waypoints().get(0).name());
    }

    @Test
    void refusesHugeFilesAndFolders() throws IOException {
        Path big = dir.resolve("big.txt");
        Files.write(big, new byte[(int) XaeroWaypointFile.MAX_FILE_BYTES + 10]);
        assertThrows(IOException.class, () -> XaeroWaypointFile.read(big));
        assertThrows(IOException.class, () -> XaeroWaypointFile.read(dir));
        assertThrows(IOException.class, () -> XaeroWaypointFile.read(dir.resolve("missing.txt")));
    }

    @Test
    void capsTheNumberOfWaypointsPerFile() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < XaeroWaypointFile.MAX_WAYPOINTS + 5; i++) {
            b.append("waypoint:W").append(i).append(":W:").append(i).append(":64:0:0:false:0:s\n");
        }
        XaeroWaypointFile.Result result = XaeroWaypointFile.parse(b.toString());
        assertEquals(XaeroWaypointFile.MAX_WAYPOINTS, result.waypoints().size());
        assertEquals(5, result.skipped());
    }
}
