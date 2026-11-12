package io.nightbeam.remnantminimaps.core.importer;

import io.nightbeam.remnantminimaps.core.marker.Marker;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import io.nightbeam.remnantminimaps.core.marker.MarkerStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns Xaero's Minimap waypoints into Remnants Minimap markers. Planning is a dry run that changes
 * nothing; applying only ever adds markers (existing ones are never changed or removed) and is done
 * after a backup of {@code markers.json}. Pure logic.
 */
public final class WaypointImport {
    /** At most this many markers are added by one import. */
    public static final int MAX_IMPORT = 5_000;

    /** Minecraft's 16 text colors, which Xaero's Minimap uses for waypoint colors 0 to 15. */
    private static final int[] TEXT_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    /** What an import would do. {@code toAdd} are fresh markers, ready to be added. */
    public record Plan(List<Marker> toAdd, int found, int alreadyPresent, int unreadableLines, int unreadableFiles,
                       int unknownDimensions, int overLimit, Map<String, Integer> perDimension) {
        public boolean isEmpty() {
            return toAdd.isEmpty();
        }
    }

    private WaypointImport() {}

    /** Reads every waypoint file of a world folder and plans the import against the existing markers. */
    public static Plan plan(Path worldFolder, List<Marker> existing) {
        List<Marker> toAdd = new ArrayList<>();
        Map<String, Integer> perDimension = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Marker m : existing) {
            seen.add(key(m.dimension, m.x, m.z, m.name));
        }
        int found = 0;
        int duplicates = 0;
        int badLines = 0;
        int badFiles = 0;
        int unknownDims = 0;
        int overLimit = 0;
        for (XaeroFolders.Dimension dim : XaeroFolders.dimensions(worldFolder)) {
            for (Path file : dim.files()) {
                XaeroWaypointFile.Result result;
                try {
                    result = XaeroWaypointFile.read(file);
                } catch (IOException | RuntimeException e) {
                    badFiles++;
                    continue;
                }
                badLines += result.skipped();
                for (XaeroWaypointFile.Waypoint w : result.waypoints()) {
                    found++;
                    if (dim.dimensionId() == null) {
                        unknownDims++;
                        continue;
                    }
                    Marker marker = toMarker(w, dim.dimensionId());
                    if (!seen.add(key(marker.dimension, marker.x, marker.z, marker.name))) {
                        duplicates++;
                        continue;
                    }
                    if (toAdd.size() >= MAX_IMPORT) {
                        overLimit++;
                        continue;
                    }
                    toAdd.add(marker);
                    perDimension.merge(dim.dimensionId(), 1, Integer::sum);
                }
            }
        }
        return new Plan(List.copyOf(toAdd), found, duplicates, badLines, badFiles, unknownDims, overLimit,
                Map.copyOf(perDimension));
    }

    private static String key(String dimension, int x, int z, String name) {
        return dimension + "|" + x + "|" + z + "|" + (name == null ? "" : name.strip().toLowerCase(Locale.ROOT));
    }

    /** A Remnants Minimap marker for one Xaero waypoint. */
    public static Marker toMarker(XaeroWaypointFile.Waypoint w, String dimensionId) {
        String name = readableName(w.name());
        if (name.isEmpty()) {
            name = !w.initials().isEmpty() ? w.initials() : "Waypoint";
        }
        MarkerIcon icon = w.isDeathpoint() ? MarkerIcon.DEATH : MarkerIcon.PIN;
        Marker marker = new Marker(name, icon, dimensionId, w.x(), w.hasY() ? w.y() : 0, w.z(), w.hasY());
        if (w.color() >= 0 && w.color() < TEXT_COLORS.length && icon.tintable()) {
            marker.color = TEXT_COLORS[w.color()];
        }
        marker.hidden = w.disabled();
        return marker;
    }

    /** Xaero's Minimap stores deathpoints under internal names; give them plain ones. */
    static String readableName(String name) {
        return switch (name) {
            case "gui.xaero_deathpoint" -> "Deathpoint";
            case "gui.xaero_deathpoint_old" -> "Old deathpoint";
            default -> name.startsWith("gui.xaero_") ? name.substring("gui.xaero_".length()).replace('_', ' ') : name;
        };
    }

    /**
     * Copies {@code markers.json} next to itself as {@code markers.json.bak-<UTC time>} before an import.
     *
     * @return the backup, or null when there was no file to back up yet
     */
    public static Path backup(Path markersFile, Instant now) throws IOException {
        if (!Files.isRegularFile(markersFile)) {
            return null;
        }
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC).format(now);
        Path target = markersFile.resolveSibling(markersFile.getFileName() + ".bak-" + stamp);
        int n = 1;
        while (Files.exists(target)) {
            target = markersFile.resolveSibling(markersFile.getFileName() + ".bak-" + stamp + "-" + n++);
        }
        Files.copy(markersFile, target, StandardCopyOption.COPY_ATTRIBUTES);
        return target;
    }

    /** Adds the planned markers. Existing markers are left as they are. */
    public static int apply(Plan plan, MarkerStore store) {
        for (Marker marker : plan.toAdd()) {
            store.add(marker.copy());
        }
        return plan.toAdd().size();
    }
}
