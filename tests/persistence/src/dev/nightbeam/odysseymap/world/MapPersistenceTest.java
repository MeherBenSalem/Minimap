package dev.nightbeam.odysseymap.world;

import dev.nightbeam.odysseymap.client.ClientEvents;
import dev.nightbeam.odysseymap.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Runs actual persistence, cache, scanner, session and common lifecycle code with narrow Minecraft test doubles. */
public class MapPersistenceTest {
    private static final ResourceKey<Level> OVERWORLD = dimension("minecraft:overworld");
    private static final ResourceKey<Level> NETHER = dimension("minecraft:the_nether");
    private static final int COLOR = 0xff123456;
    private static int passed;
    private static Path temporaryRoot;

    public static void main(String[] args) throws Exception {
        temporaryRoot = Files.createTempDirectory("odyssey-persistence-tests-");
        try {
        run("save/reopen, all pixels and negative coordinates", MapPersistenceTest::roundTrip);
        run("dimension switching and truncated-key collision isolation", MapPersistenceTest::dimensions);
        run("two worlds and same-session world switching", MapPersistenceTest::worlds);
        run("server address, port, case and unsafe-path isolation", MapPersistenceTest::servers);
        run("unloaded chunks preserve explored pixels", MapPersistenceTest::unloaded);
        run("render dirty flag cannot suppress disk save", MapPersistenceTest::dirtyFlags);
        run("autosave budget and no blank-file churn", MapPersistenceTest::saveBudget);
        run("failed writes retained across unload/world switch and retried", MapPersistenceTest::writeFailure);
        run("corrupt/newer/mismatched/oversized files preserved", MapPersistenceTest::invalidFiles);
        run("failed writes cannot starve healthy tiles", MapPersistenceTest::retryFairness);
        run("unwritable tiles cannot monopolize healthy eviction", MapPersistenceTest::evictionFairness);
        run("atomic replacements never expose partial tiles", MapPersistenceTest::atomicWrites);
        run("progressive wide render is bounded and does not reload completed tiles", MapPersistenceTest::wideRender);
        run("actual texture retains pixels across eviction and resets changed views", MapPersistenceTest::textureRetention);
        run("edits survive queued reads and refresh after eviction", MapPersistenceTest::queuedEdits);
        run("transient read failures recover without losing existing pixels", MapPersistenceTest::readFailure);
        run("permission-denied reads preserve previous exploration", MapPersistenceTest::permissionFailure);
        run("rejoined-world pending saves refresh its disk index", MapPersistenceTest::pendingIndex);
        run("clean and sampled-zero unreadable tiles recover correctly", MapPersistenceTest::cleanReadFailure);
        run("fresh install preserves existing waypoint/config files", MapPersistenceTest::legacyFiles);
        run("actual unload/logout/reopen lifecycle in both event orders", MapPersistenceTest::lifecycle);
        run("scanner dimension/reconnect preserves loaded tiles", MapPersistenceTest::scannerLifecycle);
        run("periodic session flush and deferred identity", MapPersistenceTest::sessionFlush);
        System.out.println("PASS: " + passed + " persistence regression scenarios");
        } finally {
            try (var paths = Files.walk(temporaryRoot)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void roundTrip() throws Exception {
        Path root = temporary(); TileCache cache = cache(root);
        Tile original = cache.getOrCreate(OVERWORLD, -128, 256);
        for (int z = 0; z < Tile.SIZE; z++) for (int x = 0; x < Tile.SIZE; x++) original.setPixel(x, z, COLOR ^ (z * Tile.SIZE + x));
        int[] expected = original.snapshot().pixels();
        cache.endSession();
        Tile restored = cache(root).getOrCreate(OVERWORLD, -128, 256);
        check(Arrays.equals(expected, restored.snapshot().pixels()), "all pixels must survive a fresh process-like cache");
        check(!restored.needsSaving(), "loaded tiles must not immediately rewrite");
        check(restored.isDirty(), "restored tiles must render");
        check(TileCache.alignTile(-65) == -128 && TileCache.alignTile(-64) == 0 && TileCache.alignTile(64) == 128, "tile edges");
    }

    private static void dimensions() throws Exception {
        Path root = temporary(); TileCache cache = cache(root);
        put(cache, OVERWORLD, COLOR); put(cache, NETHER, 0xffabcdef);
        check(cache.get(OVERWORLD, 0, 0) == null, "get cannot leak current dimension");
        check(pixel(cache, OVERWORLD) == COLOR && pixel(cache, NETHER) == 0xffabcdef, "dimension round-trip");
        ResourceKey<Level> one = null, two = null;
        java.util.Map<Long, ResourceKey<Level>> hashes = new java.util.HashMap<>();
        for (int i = 0; i < 10000 && two == null; i++) {
            ResourceKey<Level> dim = dimension("test:dimension_" + i);
            ResourceKey<Level> previous = hashes.putIfAbsent(TileCache.tileKey(dim, 0, 0), dim);
            if (previous != null) { one = previous; two = dim; }
        }
        check(one != null && two != null, "fixture must exercise legacy packed dimension collision");
        put(cache, one, COLOR); put(cache, two, 0xff555555);
        check(pixel(cache, one) == COLOR && pixel(cache, two) == 0xff555555, "full dimension identity must isolate legacy key collisions");
    }

    private static void worlds() throws Exception {
        Path worldA = TileStorage.singleplayerDirectory(temporary());
        Path worldB = TileStorage.singleplayerDirectory(temporary());
        TileCache cache = cache(worldA); put(cache, OVERWORLD, COLOR);
        cache.setStorageRoot(worldB); check(pixel(cache, OVERWORLD) == 0, "world B starts unknown");
        put(cache, OVERWORLD, 0xffabcdef); cache.setStorageRoot(worldA);
        check(pixel(cache, OVERWORLD) == COLOR, "world A restored");
        cache.setStorageRoot(worldB); check(pixel(cache, OVERWORLD) == 0xffabcdef, "world B restored");
    }

    private static void servers() throws Exception {
        Path config = temporary();
        Path first = TileStorage.multiplayerDirectory(config, "EXAMPLE.test:25565");
        Path same = TileStorage.multiplayerDirectory(config, " example.TEST:25565 ");
        Path port = TileStorage.multiplayerDirectory(config, "example.test:25566");
        Path host = TileStorage.multiplayerDirectory(config, "other.test:25565");
        check(first.equals(same) && !first.equals(port) && !first.equals(host), "server normalization and isolation");
        check(TileStorage.multiplayerDirectory(config, "../../escape").startsWith(config), "hash prevents path traversal");
        TileCache cache = cache(first); put(cache, OVERWORLD, COLOR);
        cache.setStorageRoot(port); check(pixel(cache, OVERWORLD) == 0, "distinct port must not share exploration");
        cache.setStorageRoot(first); check(pixel(cache, OVERWORLD) == COLOR, "server reconnect");
    }

    private static void unloaded() throws Exception {
        TileCache cache = cache(temporary()); ColumnSampler sampler = new ColumnSampler();
        cache = new TileCache(sampler); cache.setStorageRoot(temporary()); put(cache, OVERWORLD, COLOR);
        Level level = new Level(OVERWORLD); level.loaded = false;
        cache.samplePixel(level, 0, 0); check(pixel(cache, OVERWORLD) == COLOR && sampler.calls == 0, "unloaded samples never erase or invoke sampler");
        int count = cache.getTiles().size(); cache.samplePixel(level, 100000, 100000);
        check(cache.getTiles().size() == count, "unloaded panning must not allocate empty tiles");
        level.loaded = true; level.color = 0xffabcdef; cache.samplePixel(level, 0, 0);
        check(pixel(cache, OVERWORLD) == 0xffabcdef, "loaded terrain updates still work");
    }

    private static void dirtyFlags() throws Exception {
        Path root = temporary(); TileCache cache = cache(root); put(cache, OVERWORLD, COLOR);
        Tile tile = cache.getOrCreate(OVERWORLD, 0, 0); tile.clearDirty(); cache.flush(8);
        check(pixel(cache(root), OVERWORLD) == COLOR, "renderer clear cannot clear pending storage change");
        Tile.Snapshot old = tile.snapshot(); tile.setPixel(64, 64, 0xffabcdef); tile.markSaved(old.revision());
        check(tile.needsSaving(), "change during save must remain dirty");
        cache.markAllDirty(); cache.flush(8); check(!tile.needsSaving(), "latest revision saved");
    }

    private static void saveBudget() throws Exception {
        Path root = temporary(); TileCache cache = cache(root);
        cache.getOrCreate(OVERWORLD, 0, 0); cache.flush(8); check(tileFiles(root) == 0, "blank rendering does not produce disk files");
        for (int i = 0; i < 10; i++) cache.getOrCreate(OVERWORLD, i * 128, 0).setPixel(0, 0, COLOR);
        cache.flush(3); check(tileFiles(root) == 3, "flush limited to requested budget");
        cache.flush(3); check(tileFiles(root) == 6, "next flush advances");
        cache.endSession(); check(tileFiles(root) == 10, "logout flushes remainder");
    }

    private static void writeFailure() throws Exception {
        Path parent = temporary(); Path root = parent.resolve("blocked"); Files.writeString(root, "block directory creation");
        TileCache cache = cache(root); put(cache, OVERWORLD, COLOR); cache.endSession();
        Path other = temporary(); cache.setStorageRoot(other); check(pixel(cache, OVERWORLD) == 0, "failed old world never leaks into next world");
        put(cache, OVERWORLD, 0xffabcdef);
        Files.delete(root); cache.flush(8);
        check(pixel(cache(root), OVERWORLD) == COLOR, "old world data retried after disk recovers");
        cache.setStorageRoot(root); check(pixel(cache, OVERWORLD) == COLOR, "retry target stays original world");
        check(pixel(cache(other), OVERWORLD) == 0xffabcdef, "new world's data saved separately");
    }

    private static void retryFairness() throws Exception {
        Path parent = temporary(); Path failed = parent.resolve("blocked"); Files.writeString(failed, "blocked");
        TileCache cache = cache(failed);
        for (int i = 0; i < 12; i++) cache.getOrCreate(OVERWORLD, i * 128, 0).setPixel(64, 64, COLOR + i);
        cache.endSession(); Path healthy = temporary(); cache.setStorageRoot(healthy); put(cache, OVERWORLD, COLOR);
        cache.flush(8); cache.flush(8);
        check(pixel(cache(healthy), OVERWORLD) == COLOR, "failed pending writes cannot starve healthy active data");
        Files.delete(failed); cache.flush(Integer.MAX_VALUE);
        check(tileFiles(failed) == 12, "all failed data eventually retried");
    }

    private static void evictionFairness() throws Exception {
        for (boolean corrupt : new boolean[] {false, true}) {
            Path root = temporary(); Path file = TileStorage.tilePath(root, OVERWORLD.location().toString(), 0, 0);
            Files.createDirectories(file.getParent());
            if (corrupt) Files.writeString(file, "corrupt tile"); else Files.createDirectory(file);
            TileCache cache = cache(root); put(cache, OVERWORLD, COLOR);
            for (int i = 1; i < 400; i++) cache.getOrCreate(OVERWORLD, i * 128, 0).setPixel(64, 64, COLOR + i);
            check(cache.getTiles().size() <= 256, "one corrupt/unreadable tile cannot make healthy archive unbounded");
            check(tileFiles(root) >= 100, "healthy dirty tiles evicted to storage around blocked tile");
            check(cache.getOrCreate(OVERWORLD, 0, 0).getPixel(64, 64) == COLOR, "unsaved blocked exploration retained");
        }
    }

    private static void invalidFiles() throws Exception {
        for (int kind = 0; kind < 6; kind++) {
            Path root = temporary(); Path file = TileStorage.tilePath(root, OVERWORLD.location().toString(), 0, 0);
            Tile tile = new Tile(); tile.setPixel(64, 64, COLOR);
            TileStorage.save(file, OVERWORLD.location().toString(), 0, 0, tile.snapshot());
            byte[] bytes = Files.readAllBytes(file);
            switch (kind) {
                case 0 -> bytes = Arrays.copyOf(bytes, 20);
                case 1 -> ByteBuffer.wrap(bytes).putInt(4, 99);
                case 2 -> ByteBuffer.wrap(bytes).putInt(8, 128);
                case 3 -> ByteBuffer.wrap(bytes).putInt(16, Integer.MAX_VALUE);
                case 4 -> bytes = Arrays.copyOf(bytes, 70000);
                case 5 -> bytes = Arrays.copyOf(bytes, bytes.length + 1);
                default -> throw new AssertionError();
            }
            Files.write(file, bytes); TileCache cache = cache(root); put(cache, OVERWORLD, 0xffabcdef); cache.endSession();
            check(Arrays.equals(bytes, Files.readAllBytes(file)), "invalid file preserved for kind " + kind);
            cache.setStorageRoot(root); check(pixel(cache, OVERWORLD) == 0xffabcdef, "fresh exploration retained in memory for corrupt tile");
        }
    }

    private static void atomicWrites() throws Exception {
        Path root = temporary(); String dimension = OVERWORLD.location().toString(); Path path = TileStorage.tilePath(root, dimension, 0, 0);
        Tile tile = new Tile(); Arrays.fill(tile.getPixels(), COLOR);
        TileStorage.save(path, dimension, 0, 0, tile.snapshot());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    int[] pixels = TileStorage.load(path, dimension, 0, 0).snapshot().pixels();
                    for (int pixel : pixels) check(pixel == pixels[0], "reader observed partial replacement");
                }
            } catch (Throwable e) { failure.set(e); }
        });
        reader.start();
        for (int i = 0; i < 30; i++) {
            Arrays.fill(tile.getPixels(), COLOR + i); TileStorage.save(path, dimension, 0, 0, tile.snapshot());
        }
        reader.join(); if (failure.get() != null) throw new AssertionError("atomic tile reader", failure.get());
        try (var files = Files.walk(root)) { check(files.noneMatch(p -> p.toString().endsWith(".tmp")), "temporary files cleaned"); }
        byte[] before = Files.readAllBytes(path);
        try { TileStorage.save(path, "x".repeat(513), 0, 0, tile.snapshot()); throw new AssertionError("invalid dimension accepted"); }
        catch (IOException expected) { check(Arrays.equals(before, Files.readAllBytes(path)), "rejected save preserves prior bytes"); }
    }

    private static void wideRender() throws Exception {
        Path root = temporary(); TileCache cache = cache(root);
        for (int i = 0; i < 300; i++) cache.getOrCreate(OVERWORLD, i * 128, 0).setPixel(64, 64, COLOR + i);
        cache.endSession(); cache.setStorageRoot(root);
        cache.beginRender(new TileCache.RenderView(OVERWORLD, 512, 512, 0, 0, 16, false));
        for (int i = 0; i < 300; i++) check(cache.getForRender(OVERWORLD, i * 128, 0) == null, "cold render lookup is deferred");
        cache.loadRenderTiles(8); check(cache.getTiles().size() == 8, "cold tile read budget");
        int seen = 0;
        for (int batch = 0; batch < 40; batch++) {
            for (int i = 0; i < 300; i++) {
                Tile tile = cache.getForRender(OVERWORLD, i * 128, 0);
                if (tile != null) check(tile.getPixel(64, 64) == COLOR + i, "stored pixel decoded");
            }
            seen = Math.max(seen, cache.getTiles().size());
            cache.loadRenderTiles(8);
        }
        check(seen <= 256 && cache.getTiles().size() <= 256, "resident tile arrays bounded even beyond viewport cap");
        long version = cache.getContentVersion();
        for (int i = 0; i < 300; i++) cache.getForRender(OVERWORLD, i * 128, 0);
        cache.loadRenderTiles(8); check(cache.getContentVersion() == version, "completed evicted tiles are not reread for unchanged texture");
        int before = cache.getTiles().size();
        for (int i = 300; i < 2000; i++) check(cache.getForRender(OVERWORLD, i * 128, 0) == null, "unknown terrain renders blank without allocation");
        cache.loadRenderTiles(8); check(cache.getTiles().size() == before, "unexplored view does not allocate blank tile arrays");
        cache.getOrCreate(OVERWORLD, 400 * 128, 0).setPixel(64, 64, COLOR);
        check(cache.getForRender(OVERWORLD, 400 * 128, 0).getPixel(64, 64) == COLOR, "newly sampled terrain overrides a completed blank");
    }

    private static void textureRetention() throws Exception {
        Path save = temporary(); Path root = TileStorage.singleplayerDirectory(save);
        TileCache preparation = cache(root);
        for (int z = -8; z <= 8; z++) for (int x = -8; x <= 8; x++) {
            Tile tile = preparation.getOrCreate(OVERWORLD, x * 128, z * 128);
            for (int pz = 0; pz < 128; pz += 16) for (int px = 0; px < 128; px += 16) tile.setPixel(px, pz, COLOR);
        }
        preparation.endSession(); Services.configDir = temporary(); OdysseyMapClient.init();
        Minecraft mc = singleplayer(save, OVERWORLD); OdysseyMapClient.tickSession(mc);
        var texture = OdysseyMapClient.getMinimapTexture(); var cache = OdysseyMapClient.getTileCache();
        texture.compose(mc, 128, 128, 0, 0, false, 16);
        check(net.minecraft.client.renderer.texture.DynamicTexture.lastImage.getPixelRGBA(0, 0) == 0, "cold output initially blank");
        for (int tick = 0; tick < 40; tick++) {
            OdysseyMapClient.tickSession(mc); texture.compose(mc, 128, 128, 0, 0, false, 16);
            check(cache.getTiles().size() <= 256, "actual render path resident memory bounded");
        }
        var image = net.minecraft.client.renderer.texture.DynamicTexture.lastImage;
        check(image.getPixelRGBA(0, 0) == COLOR && image.getPixelRGBA(127, 127) == COLOR, "retained texture includes evicted first and latest tiles");
        long version = cache.getContentVersion(); OdysseyMapClient.tickSession(mc); texture.compose(mc, 128, 128, 0, 0, false, 16);
        check(cache.getContentVersion() == version, "stable texture does not reread archive");
        texture.compose(mc, 128, 128, 1000000, 1000000, false, 16);
        check(image.getPixelRGBA(0, 0) == 0 && image.getPixelRGBA(127, 127) == 0, "new view clears old pixels");
        OdysseyMapClient.tickSession(mc); texture.compose(mc, 128, 128, 1000000, 1000000, false, 16);
        check(image.getPixelRGBA(0, 0) == 0, "superseded old load queue cannot contaminate new view");
        OdysseyMapClient.reset();
    }

    private static void queuedEdits() throws Exception {
        Path root = temporary(); TileCache preparation = cache(root); put(preparation, OVERWORLD, COLOR); preparation.endSession();
        TileCache cache = cache(root); cache.beginRender(new TileCache.RenderView(OVERWORLD, 128, 128, 0, 0, 1, false));
        check(cache.getForRender(OVERWORLD, 0, 0) == null, "existing archive read queued");
        Level level = new Level(OVERWORLD); level.color = 0xffabcdef; cache.samplePixel(level, 0, 0);
        cache.loadRenderTiles(8); check(pixel(cache, OVERWORLD) == 0xffabcdef, "queued old read cannot overwrite newer sampled tile");
        cache.getForRender(OVERWORLD, 0, 0); // Compose has displayed latest value.
        level.color = 0xff555555; cache.samplePixel(level, 0, 0);
        for (int i = 1; i < 300; i++) cache.getOrCreate(OVERWORLD, i * 128, 0);
        check(cache.get(OVERWORLD, 0, 0) == null, "edited tile actually evicted");
        check(cache.getForRender(OVERWORLD, 0, 0) == null, "edited eviction schedules a cold refresh"); cache.loadRenderTiles(8);
        check(cache.getForRender(OVERWORLD, 0, 0).getPixel(64, 64) == 0xff555555, "edited evicted tile refreshes composed data");
    }

    private static void readFailure() throws Exception {
        Path root = temporary(); String dim = OVERWORLD.location().toString(); Path file = TileStorage.tilePath(root, dim, 0, 0);
        Tile previous = new Tile(); previous.setPixel(0, 0, COLOR); TileStorage.save(file, dim, 0, 0, previous.snapshot());
        Path backup = file.resolveSibling("retained-backup"); Files.move(file, backup); Files.createDirectory(file);
        TileCache cache = cache(root); cache.getOrCreate(OVERWORLD, 0, 0).setPixel(64, 64, 0xffabcdef); cache.endSession();
        Files.delete(file); Files.move(backup, file); cache.flush(8);
        Tile recovered = cache(root).getOrCreate(OVERWORLD, 0, 0);
        check(recovered.getPixel(0, 0) == COLOR && recovered.getPixel(64, 64) == 0xffabcdef, "recover old data and newly explored pixels together");
    }

    private static void permissionFailure() throws Exception {
        Path root = temporary(); String dim = OVERWORLD.location().toString(); Path file = TileStorage.tilePath(root, dim, 0, 0);
        if (!Files.getFileStore(root).supportsFileAttributeView("posix")) return;
        Tile previous = new Tile(); previous.setPixel(0, 0, COLOR); TileStorage.save(file, dim, 0, 0, previous.snapshot());
        var permissions = Files.getPosixFilePermissions(file.getParent()); TileCache cache = cache(root);
        try {
            Files.setPosixFilePermissions(file.getParent(), java.util.Set.of());
            cache.getOrCreate(OVERWORLD, 0, 0).setPixel(64, 64, 0xffabcdef); cache.endSession();
        } finally {
            Files.setPosixFilePermissions(file.getParent(), permissions);
        }
        cache.flush(8); Tile recovered = cache(root).getOrCreate(OVERWORLD, 0, 0);
        check(recovered.getPixel(0, 0) == COLOR && recovered.getPixel(64, 64) == 0xffabcdef, "permission recovery merges rather than overwrites exploration");
    }

    private static void pendingIndex() throws Exception {
        Path root = temporary(); Path directory = TileStorage.tilePath(root, OVERWORLD.location().toString(), 0, 0).getParent();
        Files.createDirectories(directory);
        if (!Files.getFileStore(directory).supportsFileAttributeView("posix")) return;
        var original = Files.getPosixFilePermissions(directory); TileCache cache = cache(root);
        try {
            Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
            put(cache, OVERWORLD, COLOR); cache.endSession(); cache.setStorageRoot(root);
            cache.beginRender(new TileCache.RenderView(OVERWORLD, 128, 128, 0, 0, 1, false));
        } finally {
            Files.setPosixFilePermissions(directory, original);
        }
        cache.flush(8); cache.getForRender(OVERWORLD, 0, 0); cache.loadRenderTiles(8);
        Tile tile = cache.getForRender(OVERWORLD, 0, 0);
        check(tile != null && tile.getPixel(64, 64) == COLOR, "successful old pending write is visible after rejoining its world");
    }

    private static void cleanReadFailure() throws Exception {
        for (boolean sampleZero : new boolean[] {false, true}) {
            Path root = temporary(); String dim = OVERWORLD.location().toString(); Path file = TileStorage.tilePath(root, dim, 0, 0);
            Tile previous = new Tile(); previous.setPixel(64, 64, COLOR); TileStorage.save(file, dim, 0, 0, previous.snapshot());
            Path backup = file.resolveSibling("retained-backup"); Files.move(file, backup); Files.createDirectory(file);
            TileCache cache = cache(root); Tile tile = cache.getOrCreate(OVERWORLD, 0, 0);
            if (sampleZero) tile.setPixel(64, 64, 0);
            cache.endSession(); Files.delete(file); Files.move(backup, file); cache.flush(8); cache.setStorageRoot(root);
            int expected = sampleZero ? 0 : COLOR;
            check(pixel(cache, OVERWORLD) == expected && pixel(cache(root), OVERWORLD) == expected, "clean/zero observations survive read recovery");
        }
    }

    private static void legacyFiles() throws Exception {
        Path save = temporary(); Path config = temporary(); Path waypoint = config.resolve("waypoints.json");
        byte[] original = "[{\"label\":\"Legacy waypoint\",\"dimension\":\"minecraft:overworld\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(waypoint, original); Files.writeString(save.resolve("level.dat"), "vanilla world placeholder");
        TileCache cache = cache(TileStorage.singleplayerDirectory(save)); put(cache, OVERWORLD, COLOR); cache.endSession();
        check(Arrays.equals(Files.readAllBytes(waypoint), original), "waypoints unchanged");
        check(Files.readString(save.resolve("level.dat")).equals("vanilla world placeholder"), "existing world unchanged");
    }

    private static void lifecycle() throws Exception {
        for (boolean logoutFirst : new boolean[] {false, true}) {
            Path save = temporary(); Services.configDir = temporary(); OdysseyMapClient.init();
            Minecraft mc = singleplayer(save, OVERWORLD); ClientEvents events = new ClientEvents(); events.onClientTick(mc);
            OdysseyMapClient.getTileCache().getOrCreate(OVERWORLD, 4096, 0).setPixel(64, 64, COLOR);
            if (logoutFirst) { events.onPlayerLogout(); events.onLevelUnload(); }
            else { events.onLevelUnload(); events.onPlayerLogout(); }
            events.onPlayerLogout(); // duplicate disconnect is harmless
            OdysseyMapClient.init(); mc = singleplayer(save, OVERWORLD); events.onClientTick(mc);
            check(OdysseyMapClient.getTileCache().getOrCreate(OVERWORLD, 4096, 0).getPixel(64, 64) == COLOR,
                    "loaded distant exploration survives first scanner tick and event order " + logoutFirst);
            events.onPlayerLogout();
        }
    }

    private static void scannerLifecycle() throws Exception {
        Path save = temporary(); Services.configDir = temporary(); OdysseyMapClient.init(); ClientEvents events = new ClientEvents();
        Minecraft mc = singleplayer(save, OVERWORLD); events.onClientTick(mc);
        TileCache cache = OdysseyMapClient.getTileCache(); cache.getOrCreate(OVERWORLD, 4096, 0).setPixel(64, 64, COLOR);
        events.onLevelUnload(); mc.level = new ClientLevel(NETHER); mc.player = new LocalPlayer(mc.level); events.onClientTick(mc);
        cache.getOrCreate(NETHER, 4096, 0).setPixel(64, 64, 0xffabcdef);
        events.onLevelUnload(); mc.level = new ClientLevel(OVERWORLD); mc.player = new LocalPlayer(mc.level); events.onClientTick(mc);
        check(cache.getOrCreate(OVERWORLD, 4096, 0).getPixel(64, 64) == COLOR, "scanner must not clear loaded exploration on dimension change");
        events.onPlayerLogout(); mc = singleplayer(save, OVERWORLD); events.onClientTick(mc);
        check(cache.getOrCreate(OVERWORLD, 4096, 0).getPixel(64, 64) == COLOR, "scanner restart in same dimension");
        events.onPlayerLogout();
    }

    private static void sessionFlush() throws Exception {
        Path save = temporary(); Services.configDir = temporary(); OdysseyMapClient.init();
        Minecraft unknown = singleplayer(save, OVERWORLD); unknown.singleplayerServer = null;
        check(!OdysseyMapClient.tickSession(unknown), "no identity means do not use shared fallback");
        Minecraft mc = singleplayer(save, OVERWORLD); OdysseyMapClient.tickSession(mc);
        put(OdysseyMapClient.getTileCache(), OVERWORLD, COLOR);
        for (int i = 0; i < 20; i++) OdysseyMapClient.tickSession(mc);
        check(pixel(cache(TileStorage.singleplayerDirectory(save)), OVERWORLD) == COLOR, "periodic save before logout");
        OdysseyMapClient.reset();
        Minecraft multiplayer = new Minecraft(); multiplayer.level = new ClientLevel(OVERWORLD); multiplayer.player = new LocalPlayer(multiplayer.level);
        multiplayer.currentServer = new Minecraft.ServerData("example.test:25565");
        check(OdysseyMapClient.tickSession(multiplayer), "server session binds"); put(OdysseyMapClient.getTileCache(), OVERWORLD, 0xffabcdef); OdysseyMapClient.reset();
        check(pixel(cache(TileStorage.multiplayerDirectory(Services.configDir, "example.test:25565")), OVERWORLD) == 0xffabcdef, "server resolver wired to persistence");
    }

    private static Minecraft singleplayer(Path save, ResourceKey<Level> dimension) {
        Minecraft mc = new Minecraft(); mc.level = new ClientLevel(dimension); mc.player = new LocalPlayer(mc.level);
        mc.singleplayerServer = new Minecraft.IntegratedServer(save); return mc;
    }
    private static Path temporary() throws IOException { return Files.createTempDirectory(temporaryRoot, "scenario-"); }
    private static ResourceKey<Level> dimension(String id) { return new ResourceKey<>(new ResourceLocation(id)); }
    private static TileCache cache(Path root) { TileCache cache = new TileCache(new ColumnSampler()); cache.setStorageRoot(root); return cache; }
    private static void put(TileCache cache, ResourceKey<Level> dim, int color) { cache.getOrCreate(dim, 0, 0).setPixel(64, 64, color); }
    private static int pixel(TileCache cache, ResourceKey<Level> dim) { return cache.getOrCreate(dim, 0, 0).getPixel(64, 64); }
    private static long tileFiles(Path root) throws IOException { try (var files = Files.walk(root)) { return files.filter(p -> p.toString().endsWith(".tile")).count(); } }
    private static void check(boolean condition, String description) { if (!condition) throw new AssertionError(description); }
    private static void run(String name, Test test) throws Exception { test.run(); passed++; System.out.println("PASS: " + name); }
    private interface Test { void run() throws Exception; }
}
