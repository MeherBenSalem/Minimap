package io.nightbeam.remnantminimaps.core.importer;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds Xaero's Minimap waypoint folders in the game directory and maps their dimension folders to
 * dimension ids. Only the two standard locations are searched, symbolic links are never followed,
 * and every listing is bounded. Pure logic (plain file access only).
 *
 * <pre>
 * &lt;game&gt;/xaero/minimap/&lt;world&gt;/dim%0/mw$default_1.txt     (Xaero's Minimap 24.3 and later)
 * &lt;game&gt;/XaeroWaypoints/&lt;world&gt;/dim%-1/waypoints.txt       (older versions)
 * </pre>
 */
public final class XaeroFolders {
    /** Standard locations below the game directory, newest first. */
    public static final List<String> ROOTS = List.of("xaero/minimap", "XaeroWaypoints");
    public static final int MAX_WORLDS = 256;
    public static final int MAX_DIMENSIONS = 64;
    public static final int MAX_FILES_PER_DIMENSION = 32;

    private static final Pattern NUMERIC = Pattern.compile("-?\\d{1,9}");
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** A folder with one world's waypoints. */
    public record World(Path folder, String name, long lastModified) {}

    /** One dimension folder of a world, its dimension id (null when unknown) and its waypoint files. */
    public record Dimension(Path folder, String dimensionId, List<Path> files) {}

    private XaeroFolders() {}

    /** World folders under the standard locations, most recently changed first. */
    public static List<World> findWorlds(Path gameDir) {
        List<World> worlds = new ArrayList<>();
        for (String root : ROOTS) {
            Path dir = gameDir.resolve(root);
            if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            for (Path child : list(dir, MAX_WORLDS)) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) && !dimensions(child).isEmpty()) {
                    worlds.add(new World(child, child.getFileName().toString(), lastModified(child)));
                }
            }
        }
        worlds.sort(Comparator.comparingLong(World::lastModified).reversed().thenComparing(World::name));
        return worlds;
    }

    /** Dimension folders ({@code dim%...}) of a world folder, sorted by name. */
    public static List<Dimension> dimensions(Path worldFolder) {
        List<Dimension> out = new ArrayList<>();
        for (Path child : list(worldFolder, MAX_DIMENSIONS)) {
            String name = child.getFileName().toString();
            if (!name.startsWith("dim%") || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            List<Path> files = new ArrayList<>();
            for (Path file : list(child, MAX_FILES_PER_DIMENSION)) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".txt") && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(file);
                }
            }
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));
            out.add(new Dimension(child, dimensionId(name).orElse(null), List.copyOf(files)));
        }
        out.sort(Comparator.comparing(d -> d.folder().getFileName().toString()));
        return out;
    }

    /**
     * Dimension id for a Xaero dimension folder name: {@code dim%0} is the Overworld, {@code dim%-1}
     * the Nether, {@code dim%1} the End; {@code dim%namespace$path} is {@code namespace:path}.
     */
    public static Optional<String> dimensionId(String folderName) {
        if (!folderName.startsWith("dim%")) {
            return Optional.empty();
        }
        String key = folderName.substring(4);
        if (NUMERIC.matcher(key).matches()) {
            return switch (Integer.parseInt(key)) {
                case 0 -> Optional.of("minecraft:overworld");
                case -1 -> Optional.of("minecraft:the_nether");
                case 1 -> Optional.of("minecraft:the_end");
                default -> Optional.empty();
            };
        }
        int split = key.indexOf('$');
        if (split <= 0) {
            return Optional.empty();
        }
        String id = (key.substring(0, split) + ":" + key.substring(split + 1).replace('$', '/')).toLowerCase(Locale.ROOT);
        return RESOURCE.matcher(id).matches() ? Optional.of(id) : Optional.empty();
    }

    /**
     * Index of the world folder that most likely belongs to the world the player is in, or 0.
     *
     * @param worldId Remnants Minimap's id of the current world: {@code sp_<save folder>} or {@code mp_<address>}
     */
    public static int bestMatch(List<World> worlds, String worldId) {
        if (worldId == null) {
            return 0;
        }
        String id = worldId.toLowerCase(Locale.ROOT);
        String wanted = id.length() > 3 ? id.substring(3) : id;
        for (int i = 0; i < worlds.size(); i++) {
            String name = simplify(worlds.get(i).name());
            if (id.startsWith("sp_") && name.equals(wanted)) {
                return i;
            }
            if (id.startsWith("mp_") && name.startsWith("multiplayer_") && name.substring(12).startsWith(wanted)) {
                return i;
            }
        }
        return 0;
    }

    /** Same simplification Remnants Minimap applies to world names. */
    static String simplify(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return s.isEmpty() ? "world" : s;
    }

    private static List<Path> list(Path dir, int limit) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isSymbolicLink(p)) {
                    continue;
                }
                out.add(p);
                if (out.size() >= limit) {
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            return out;
        }
        return out;
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
