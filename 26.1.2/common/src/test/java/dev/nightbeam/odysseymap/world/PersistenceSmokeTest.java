package dev.nightbeam.odysseymap.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** Headless storage/cache smoke test against the real Minecraft dependency, without test doubles. */
public final class PersistenceSmokeTest {
    private static final ResourceKey<Level> OVERWORLD = dimension("minecraft", "overworld");
    private static final ResourceKey<Level> NETHER = dimension("minecraft", "the_nether");
    private static final int COLOR = 0xff123456;

    private PersistenceSmokeTest() {}

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("odysseymap-real-persistence-");
        try {
            roundTrip(temporary.resolve("roundtrip"));
            isolation(temporary.resolve("isolation"));
            boundedCache(temporary.resolve("bounded"));
            corruptPreservation(temporary.resolve("corrupt"));
            readRecovery(temporary.resolve("read"));
            writeRecovery(temporary.resolve("write"));
            System.out.println("PASS: 6 persistence smoke scenarios against real Minecraft classes");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void roundTrip(Path root) {
        TileCache cache = cache(root);
        Tile tile = cache.getOrCreate(OVERWORLD, -128, 256);
        for (int z = 0; z < Tile.SIZE; z++) {
            for (int x = 0; x < Tile.SIZE; x++) tile.setPixel(x, z, COLOR ^ (z * Tile.SIZE + x));
        }
        int[] expected = tile.snapshot().pixels();
        tile.clearDirty();
        cache.endSession();
        Tile restored = cache(root).getOrCreate(OVERWORLD, -128, 256);
        check(Arrays.equals(expected, restored.snapshot().pixels()), "every explored pixel survives fresh cache");
        check(!restored.needsSaving(), "restored terrain starts durably clean");
        check(TileCache.alignTile(-65) == -128 && TileCache.alignTile(-64) == 0
                && TileCache.alignTile(64) == 128, "negative tile boundaries");
        System.out.println("PASS: all-pixel/negative-coordinate roundtrip and independent render dirtiness");
    }

    private static void isolation(Path parent) {
        Path first = TileStorage.singleplayerDirectory(parent.resolve("world-a"));
        Path second = TileStorage.singleplayerDirectory(parent.resolve("world-b"));
        TileCache cache = cache(first);
        put(cache, OVERWORLD, 0, COLOR);
        put(cache, NETHER, 0, 0xffabcdef);
        check(pixel(cache, OVERWORLD, 0) == COLOR && pixel(cache, NETHER, 0) == 0xffabcdef,
                "dimension roundtrip remains isolated");
        cache.setStorageRoot(second);
        check(pixel(cache, OVERWORLD, 0) == 0, "another world cannot read explored terrain");
        put(cache, OVERWORLD, 0, 0xffaabbcc);
        cache.setStorageRoot(first);
        check(pixel(cache, OVERWORLD, 0) == COLOR, "switching back restores original world");
        cache.endSession();

        Path server = TileStorage.multiplayerDirectory(parent, " EXAMPLE.test:25565 ");
        check(server.equals(TileStorage.multiplayerDirectory(parent, "example.TEST:25565")),
                "equivalent server addresses share a map");
        Path otherPort = TileStorage.multiplayerDirectory(parent, "example.test:25566");
        check(!server.equals(otherPort), "different server ports remain isolated");
        check(TileStorage.multiplayerDirectory(parent, "../../escape").startsWith(parent.toAbsolutePath()),
                "server address never controls a filesystem path");
        cache = cache(server);
        put(cache, OVERWORLD, 0, COLOR);
        cache.setStorageRoot(otherPort);
        check(pixel(cache, OVERWORLD, 0) == 0, "another server cannot read explored terrain");
        cache.setStorageRoot(server);
        check(pixel(cache, OVERWORLD, 0) == COLOR, "server reconnect restores terrain");
        cache.endSession();
        System.out.println("PASS: dimension, save-folder and server isolation");
    }

    private static void boundedCache(Path root) throws Exception {
        TileCache cache = cache(root);
        for (int i = 0; i < 320; i++) {
            put(cache, OVERWORLD, i * Tile.SIZE, COLOR ^ i);
            check(cache.getTiles().size() <= 256, "normal dirty eviction keeps at most 256 tiles resident");
        }
        cache.endSession();
        check(TileStorage.listTiles(root, id(OVERWORLD)).size() == 320, "all evicted and resident tiles persisted");
        cache = cache(root);
        cache.beginRender(new TileCache.RenderView(OVERWORLD, 320, 1, 0, 0, Tile.SIZE, false));
        for (int i = 0; i < 320; i++) {
            check(cache.getForRender(OVERWORLD, i * Tile.SIZE, 0) == null, "cold render only queues reads");
        }
        Set<Integer> rendered = new HashSet<>();
        for (int tick = 0; tick < 40; tick++) {
            cache.loadRenderTiles(8);
            check(cache.getTiles().size() <= 256, "progressive render keeps residency bounded");
            int before = rendered.size();
            for (int i = 0; i < 320; i++) {
                Tile tile = cache.getForRender(OVERWORLD, i * Tile.SIZE, 0);
                if (tile != null) {
                    check(tile.getPixel(64, 64) == (COLOR ^ i), "progressive render retrieves persisted colors");
                    rendered.add(i);
                }
            }
            check(rendered.size() - before == 8, "each render tick reads exactly its eight-tile budget");
        }
        check(rendered.size() == 320, "wide render eventually covers evicted terrain");
        long version = cache.getContentVersion();
        cache.loadRenderTiles(8);
        check(version == cache.getContentVersion(), "completed render tiles are not requeued after eviction");
        cache.endSession();
        System.out.println("PASS: more than 256 explored tiles, eviction and progressive bounded reads");
    }

    private static void corruptPreservation(Path root) throws Exception {
        TileCache initial = cache(root);
        put(initial, OVERWORLD, 0, COLOR);
        initial.endSession();
        Path file = TileStorage.tilePath(root, id(OVERWORLD), 0, 0);
        byte[] valid = Files.readAllBytes(file);
        byte[] future = valid.clone();
        ByteBuffer.wrap(future).putInt(4, 2);
        for (byte[] protectedData : new byte[][] {new byte[] {1, 2, 3}, future}) {
            Files.write(file, protectedData);
            TileCache cache = cache(root);
            put(cache, OVERWORLD, 0, 0xff998877);
            cache.flush(8);
            cache.endSession();
            check(Arrays.equals(protectedData, Files.readAllBytes(file)), "corrupt/future-format files stay untouched");
        }
        System.out.println("PASS: corrupt and future-format tile protection");
    }

    private static void readRecovery(Path root) throws Exception {
        TileCache initial = cache(root);
        put(initial, OVERWORLD, 0, COLOR);
        initial.getOrCreate(OVERWORLD, 0, 0).setPixel(66, 64, 0xff554433);
        initial.endSession();
        Path file = TileStorage.tilePath(root, id(OVERWORLD), 0, 0);
        byte[] previous = Files.readAllBytes(file);
        Files.delete(file);
        Files.createDirectory(file);
        TileCache cache = cache(root);
        Tile tile = cache.getOrCreate(OVERWORLD, 0, 0);
        tile.setPixel(65, 64, 0xffabcdef);
        tile.setPixel(64, 64, 0);
        Files.delete(file);
        Files.write(file, previous);
        cache.flush(8);
        check(tile.getPixel(64, 64) == 0 && tile.getPixel(65, 64) == 0xffabcdef
                && tile.getPixel(66, 64) == 0xff554433,
                "recovery merges old terrain with even zero-valued new observations");
        cache.endSession();
        tile = cache(root).getOrCreate(OVERWORLD, 0, 0);
        check(tile.getPixel(64, 64) == 0 && tile.getPixel(65, 64) == 0xffabcdef
                && tile.getPixel(66, 64) == 0xff554433,
                "merged observations are durable");
        System.out.println("PASS: transient read recovery and merge");
    }

    private static void writeRecovery(Path parent) throws Exception {
        Files.createDirectories(parent);
        Path root = parent.resolve("temporarily-unwritable");
        Files.writeString(root, "blocks directory creation");
        TileCache cache = cache(root);
        put(cache, OVERWORLD, 0, COLOR);
        cache.endSession();
        Path other = parent.resolve("other-world");
        cache.setStorageRoot(other);
        put(cache, OVERWORLD, 0, 0xffabcdef);
        Files.delete(root);
        cache.flush(8);
        check(pixel(cache(root), OVERWORLD, 0) == COLOR, "failed writes retain their original destination");
        cache.endSession();
        check(pixel(cache(other), OVERWORLD, 0) == 0xffabcdef, "healthy new-world writes are not lost");
        System.out.println("PASS: write recovery across world switches");
    }

    private static TileCache cache(Path root) {
        TileCache cache = new TileCache(null);
        cache.setStorageRoot(root);
        return cache;
    }

    private static void put(TileCache cache, ResourceKey<Level> dimension, int x, int color) {
        cache.getOrCreate(dimension, x, 0).setPixel(64, 64, color);
    }

    private static int pixel(TileCache cache, ResourceKey<Level> dimension, int x) {
        return cache.getOrCreate(dimension, x, 0).getPixel(64, 64);
    }

    private static String id(ResourceKey<Level> dimension) { return dimension.identifier().toString(); }

    private static ResourceKey<Level> dimension(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
