package dev.nightbeam.odysseymap.marker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nightbeam.odysseymap.platform.Services;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MarkerStorage {
    private static final Logger LOG = LoggerFactory.getLogger("OdysseyMap");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<StoredMarker>>() {}.getType();

    private MarkerStorage() {}

    public static void save() {
        Path path = getPath();
        List<StoredMarker> stored = new ArrayList<>();
        for (Marker m : MarkerManager.get().getWaypoints()) {
            stored.add(StoredMarker.from(m));
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(stored, writer);
            }
        } catch (IOException e) {
            LOG.error("Failed to save waypoints", e);
        }
    }

    public static void load() {
        Path path = getPath();
        MarkerManager.get().clearWaypoints();
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            List<StoredMarker> stored = GSON.fromJson(reader, LIST_TYPE);
            if (stored == null) return;
            for (StoredMarker s : stored) {
                Identifier loc = Identifier.tryParse(s.dimension);
                if (loc == null) continue;
                ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, loc);
                int color = s.color != 0 ? s.color : MarkerType.WAYPOINT.defaultColor;
                int y = s.y != null ? s.y : Marker.UNKNOWN_Y;
                Marker marker = new Marker(
                        s.id != null ? UUID.fromString(s.id) : UUID.randomUUID(),
                        MarkerType.WAYPOINT, dim, s.x, y, s.z,
                        s.label != null ? s.label : "Waypoint", color);
                if (s.visible != null) marker.setVisible(s.visible);
                if (s.favorite != null) marker.setFavorite(s.favorite);
                MarkerManager.get().addWaypointDirect(marker);
            }
        } catch (IOException e) {
            LOG.error("Failed to load waypoints", e);
        }
    }

    private static Path getPath() {
        return Services.PLATFORM.getConfigDir().resolve("odysseymap").resolve("waypoints.json");
    }

    private static class StoredMarker {
        String id;
        String dimension;
        int x;
        Integer y;
        int z;
        String label;
        int color;
        Boolean visible;
        Boolean favorite;

        static StoredMarker from(Marker m) {
            StoredMarker s = new StoredMarker();
            s.id = m.getId().toString();
            s.dimension = m.getDimension().identifier().toString();
            s.x = m.getX();
            s.y = m.hasKnownY() ? m.getY() : null;
            s.z = m.getZ();
            s.label = m.getLabel();
            s.color = m.getColor();
            s.visible = m.isVisible() ? null : false;  // only save if hidden (default is visible)
            s.favorite = m.isFavorite() ? true : null;  // only save if favorited
            return s;
        }
    }
}
