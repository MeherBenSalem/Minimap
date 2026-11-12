package io.nightbeam.remnantminimaps.core.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one Xaero's Minimap waypoint file, the plain text file the player's own game writes to
 * {@code xaero/minimap/<world>/<dimension>/} (or {@code XaeroWaypoints/...} in older versions).
 * Each waypoint is one line:
 *
 * <pre>
 * waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw[:more fields]
 * </pre>
 *
 * Only the fields up to {@code type} are needed; later ones (teleport options, visibility, newer
 * additions) are ignored. Colons inside names are stored as two section signs (U+00A7). {@code y} may be {@code ~}
 * when unknown. Lines starting with {@code #} and {@code sets:} lines are skipped. Nothing here
 * comes from Xaero's code: the format is the one documented in the file's own header comment.
 *
 * <p>Everything is validated; a damaged line is counted and skipped, never fatal. Pure logic.
 */
public final class XaeroWaypointFile {
    /** Files larger than this are not read. A real waypoint file is a few kilobytes. */
    public static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    /** At most this many waypoints are taken from one file. */
    public static final int MAX_WAYPOINTS = 10_000;
    /** Longer names are cut. */
    public static final int MAX_NAME_LENGTH = 64;
    /** Coordinates beyond the world limit are refused. */
    public static final int MAX_HORIZONTAL = 30_000_000;
    public static final int MIN_Y = -2048;
    public static final int MAX_Y = 4096;

    /** Xaero's deathpoint types: 1 is the latest death, 2 an older one. */
    public static final int TYPE_DEATH = 1;
    public static final int TYPE_OLD_DEATH = 2;

    /** One waypoint as written by Xaero's Minimap. {@code hasY} is false when y was {@code ~}. */
    public record Waypoint(String name, String initials, int x, int y, boolean hasY, int z, int color, boolean disabled,
                           int type, String set) {
        public boolean isDeathpoint() {
            return type == TYPE_DEATH || type == TYPE_OLD_DEATH;
        }
    }

    /** Waypoints read from a file, and how many waypoint lines had to be skipped. */
    public record Result(List<Waypoint> waypoints, int skipped) {}

    private XaeroWaypointFile() {}

    /** Reads a file; throws when it cannot be read at all (missing, too large, not a regular file). */
    public static Result read(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("not a regular file: " + file.getFileName());
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("file too large: " + file.getFileName());
        }
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) {
            bytes = in.readNBytes((int) MAX_FILE_BYTES + 1);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("file too large: " + file.getFileName());
        }
        return parse(decode(bytes));
    }

    /** UTF-8 with a fallback to Latin-1, so odd bytes never stop the import. */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    public static Result parse(String text) {
        List<Waypoint> out = new ArrayList<>();
        int skipped = 0;
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        for (String raw : body.split("\r\n|\r|\n")) {
            String line = raw.strip();
            if (!line.startsWith("waypoint:")) {
                continue; // comments, "sets:" lines, blank lines
            }
            if (out.size() >= MAX_WAYPOINTS) {
                skipped++;
                continue;
            }
            Waypoint w = parseLine(line);
            if (w == null) {
                skipped++;
            } else {
                out.add(w);
            }
        }
        return new Result(List.copyOf(out), skipped);
    }

    /** Parses one {@code waypoint:} line, or returns null when it is damaged. */
    static Waypoint parseLine(String line) {
        String[] f = line.split(":", -1);
        if (f.length < 9 || !f[0].equals("waypoint")) {
            return null;
        }
        String name = cleanText(f[1].replace("\u00A7\u00A7", ":"));
        String initials = cleanText(f[2].replace("\u00A7\u00A7", ":"));
        Integer x = parseInt(f[3]);
        Integer z = parseInt(f[5]);
        if (x == null || z == null || Math.abs((long) x) > MAX_HORIZONTAL || Math.abs((long) z) > MAX_HORIZONTAL) {
            return null;
        }
        boolean hasY = !f[4].strip().equals("~");
        int y = 0;
        if (hasY) {
            Integer parsed = parseInt(f[4]);
            if (parsed == null || parsed < MIN_Y || parsed > MAX_Y) {
                return null;
            }
            y = parsed;
        }
        Integer color = parseInt(f[6]);
        String disabled = f[7].strip();
        if (!disabled.equals("true") && !disabled.equals("false")) {
            return null;
        }
        Integer type = parseInt(f[8]);
        String set = f.length > 9 ? cleanText(f[9]) : "";
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        return new Waypoint(name, initials, x, y, hasY, z, color == null ? -1 : color, disabled.equals("true"),
                type == null ? 0 : type, set);
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Drops formatting codes and control characters. */
    static String cleanText(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7') {
                i++; // skip the formatting code that follows
                continue;
            }
            if (!Character.isISOControl(c)) {
                b.append(c);
            }
        }
        return b.toString().strip();
    }
}
