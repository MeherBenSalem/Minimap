package io.nightbeam.remnantminimaps.client.session;

import io.nightbeam.remnantminimaps.RemnantMinimaps;
import io.nightbeam.remnantminimaps.core.config.RemnantConfig;
import io.nightbeam.remnantminimaps.core.marker.MarkerStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One connection to one world: its markers, its IO thread and the map of the current dimension. */
public final class MapSession {
    public final String worldId;
    public final Path worldDir;
    public final MarkerStore markers;
    private final ExecutorService io;
    private DimensionMap current;

    private MapSession(String worldId, Path worldDir) {
        this.worldId = worldId;
        this.worldDir = worldDir;
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Remnants Minimap IO");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        this.markers = new MarkerStore(worldDir.resolve("markers.json"));
        this.markers.onError(e -> RemnantMinimaps.LOG.warn("Marker storage problem in {}", worldDir, e));
        this.markers.load();
    }

    public static MapSession open(Minecraft mc, Path root) {
        String id = worldId(mc);
        return new MapSession(id, root.resolve("worlds").resolve(id));
    }

    static String worldId(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            Path path = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name = path.getFileName();
            return "sp_" + sanitize(name != null ? name.toString() : "world");
        }
        ServerData data = mc.getCurrentServer();
        if (data != null && data.ip != null) {
            return "mp_" + sanitize(data.ip);
        }
        return "unknown";
    }

    static String sanitize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return s.isEmpty() ? "world" : s;
    }

    public static String dimensionId(ResourceKey<Level> key) {
        return key.identifier().toString();
    }

    public DimensionMap dimension(String dimensionId, RemnantConfig config) {
        if (current != null && current.dimensionId.equals(dimensionId)) {
            return current;
        }
        if (current != null) {
            current.close();
        }
        Path dir = worldDir.resolve("dim").resolve(sanitize(dimensionId.replace(':', '_').replace('/', '_')));
        current = new DimensionMap(dimensionId, dir, io, config.style(), config.maxLoadedRegions);
        return current;
    }

    public DimensionMap current() {
        return current;
    }

    public void saveMarkers() {
        markers.saveIfDirty(io);
    }

    public void close() {
        if (current != null) {
            current.close();
            current = null;
        }
        markers.saveIfDirty(io);
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                RemnantMinimaps.LOG.warn("Map saving is taking long; continuing in the background");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
