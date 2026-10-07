package dev.nightbeam.odysseymap.world;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/** Bounded, versioned client map data. Each replacement is atomic on the same filesystem. */
public final class TileStorage {
    private static final int MAGIC = 0x4F44594D; // ODYM
    private static final int VERSION = 1;
    private static final int PIXELS = Tile.SIZE * Tile.SIZE;
    private static final int MAX_FILE_BYTES = PIXELS * Integer.BYTES + 20 + 514;

    private TileStorage() {}

    static final class InvalidTileException extends IOException {
        private static final long serialVersionUID = 1L;
        InvalidTileException(String message) { super(message); }
        InvalidTileException(String message, IOException cause) { super(message, cause); }
    }

    public static Path singleplayerDirectory(Path saveDirectory) {
        return saveDirectory.resolve("odysseymap").resolve("tiles-v1").toAbsolutePath().normalize();
    }

    public static Path multiplayerDirectory(Path configDirectory, String serverAddress) {
        return configDirectory.resolve("odysseymap").resolve("maps").resolve("servers")
                .resolve(hash(serverAddress.trim().toLowerCase(Locale.ROOT)))
                .resolve("tiles-v1").toAbsolutePath().normalize();
    }

    static Path tilePath(Path root, String dimension, int alignedX, int alignedZ) {
        return root.resolve(hash(dimension)).resolve(alignedX + "_" + alignedZ + ".tile");
    }

    record Coordinates(int x, int z) {}

    static List<Coordinates> listTiles(Path root, String dimension) throws IOException {
        List<Coordinates> coordinates = new ArrayList<>();
        Path directory = root.resolve(hash(dimension));
        try (var files = Files.newDirectoryStream(directory, "*.tile")) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                String[] parts = name.substring(0, name.length() - 5).split("_", -1);
                if (parts.length != 2) continue;
                try {
                    int x = Integer.parseInt(parts[0]);
                    int z = Integer.parseInt(parts[1]);
                    if (x % Tile.SIZE == 0 && z % Tile.SIZE == 0) coordinates.add(new Coordinates(x, z));
                } catch (NumberFormatException ignored) {
                    // Ignore unrelated files without interpreting them as terrain.
                }
            }
        } catch (NoSuchFileException absent) {
            // No stored exploration for this dimension yet.
        } catch (DirectoryIteratorException unavailable) {
            throw unavailable.getCause();
        }
        return coordinates;
    }

    static Tile load(Path path, String dimension, int alignedX, int alignedZ) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (NoSuchFileException absent) {
            return new Tile();
        }
        // exists/isRegularFile suppress access errors and can misclassify unreadable data as absent.
        if (!attributes.isRegularFile()) throw new IOException("Map tile is temporarily unreadable: " + path);
        long bytes = attributes.size();
        if (bytes < 22L + PIXELS * Integer.BYTES || bytes > MAX_FILE_BYTES) {
            throw new InvalidTileException("Invalid map tile length: " + path);
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION
                    || input.readInt() != alignedX || input.readInt() != alignedZ
                    || input.readInt() != PIXELS || !input.readUTF().equals(dimension)) {
                throw new InvalidTileException("Unsupported or mismatched map tile: " + path);
            }
            int[] pixels = new int[PIXELS];
            for (int i = 0; i < PIXELS; i++) pixels[i] = input.readInt();
            if (input.read() != -1) throw new InvalidTileException("Trailing map tile data: " + path);
            Tile tile = new Tile();
            tile.restore(pixels);
            return tile;
        } catch (EOFException | UTFDataFormatException malformed) {
            throw new InvalidTileException("Truncated or malformed map tile: " + path, malformed);
        }
    }

    static void save(Path path, String dimension, int alignedX, int alignedZ, Tile.Snapshot snapshot)
            throws IOException {
        if (dimension.getBytes(StandardCharsets.UTF_8).length > 512
                || snapshot.pixels().length != PIXELS) throw new IOException("Invalid map tile data");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".odysseymap-", ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(alignedX);
                output.writeInt(alignedZ);
                output.writeInt(PIXELS);
                output.writeUTF(dimension);
                for (int pixel : snapshot.pixels()) output.writeInt(pixel);
            }
            // If atomic replacement is unavailable or fails, keep the previous file and retry later.
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
