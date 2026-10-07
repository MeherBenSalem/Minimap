package dev.nightbeam.odysseymap.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/** Runs against each workspace's real Minecraft classes; no world or graphics test doubles. */
public final class PersistenceSmokeTest {
    private PersistenceSmokeTest() {}

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("odysseymap-native-smoke-");
        try {
            ResourceKey<Level> overworld = dimension("overworld");
            ResourceKey<Level> nether = dimension("the_nether");
            Path worldA = TileStorage.singleplayerDirectory(temporary.resolve("world-a"));
            Path worldB = TileStorage.singleplayerDirectory(temporary.resolve("world-b"));
            TileCache cache = cache(worldA);
            Tile original = cache.getOrCreate(overworld, -128, 256);
            for (int z = 0; z < Tile.SIZE; z++) for (int x = 0; x < Tile.SIZE; x++) original.setPixel(x, z, 0xff123456 ^ (z * Tile.SIZE + x));
            int[] expected = original.snapshot().pixels();
            cache.endSession();
            cache.setStorageRoot(worldA);
            check(Arrays.equals(expected, cache.getOrCreate(overworld, -128, 256).snapshot().pixels()), "full pixel reopen");
            cache.getOrCreate(nether, -128, 256).setPixel(64, 64, 0xffabcdef);
            check(Arrays.equals(expected, cache.getOrCreate(overworld, -128, 256).snapshot().pixels()), "dimension round-trip");
            check(cache.getOrCreate(nether, -128, 256).getPixel(64, 64) == 0xffabcdef, "nether isolation");
            cache.setStorageRoot(worldB);
            check(cache.getOrCreate(overworld, -128, 256).getPixel(64, 64) == 0, "different world blank");
            cache.setStorageRoot(worldA);
            check(Arrays.equals(expected, cache.getOrCreate(overworld, -128, 256).snapshot().pixels()), "old world restored");
            Path config = temporary.resolve("config");
            check(TileStorage.multiplayerDirectory(config, "HOST.test:25565").equals(TileStorage.multiplayerDirectory(config, "host.TEST:25565")), "server case normalization");
            check(!TileStorage.multiplayerDirectory(config, "host.test:25565").equals(TileStorage.multiplayerDirectory(config, "host.test:25566")), "server port isolation");
            for (int i = 0; i < 300; i++) cache.getOrCreate(overworld, i * 128, 0).setPixel(64, 64, 0xff001000 + i);
            check(cache.getTiles().size() <= 256, "bounded dirty eviction");
            cache.endSession();
            cache = cache(worldA);
            check(cache.getOrCreate(overworld, 299 * 128, 0).getPixel(64, 64) == 0xff001000 + 299, "evicted and live data persisted");
            Path corrupt = TileStorage.tilePath(worldA, dimensionId(overworld), 0, 0);
            byte[] previous = Files.readAllBytes(corrupt);
            ByteBuffer.wrap(previous).putInt(4, 99); Files.write(corrupt, previous);
            cache.endSession(); cache = cache(worldA);
            cache.getOrCreate(overworld, 0, 0).setPixel(64, 64, 0xff555555); cache.endSession();
            check(Arrays.equals(previous, Files.readAllBytes(corrupt)), "future format preserved");
            Path blocker = temporary.resolve("not-a-directory"); Files.writeString(blocker, "blocked");
            cache = cache(blocker); cache.getOrCreate(overworld, 0, 0).setPixel(64, 64, 0xff778899); cache.endSession();
            cache.setStorageRoot(worldB); Files.delete(blocker); cache.flush(8);
            check(cache(blocker).getOrCreate(overworld, 0, 0).getPixel(64, 64) == 0xff778899, "failed old-world write recovery");
            System.out.println("PASS: real Minecraft resource keys, full pixel reopen, world/server/dimension isolation, eviction, future-format protection and write recovery");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static ResourceKey<Level> dimension(String name) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", name));
    }
    private static String dimensionId(ResourceKey<Level> dimension) { return dimension.location().toString(); }
    private static TileCache cache(Path root) { TileCache cache = new TileCache(null); cache.setStorageRoot(root); return cache; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
