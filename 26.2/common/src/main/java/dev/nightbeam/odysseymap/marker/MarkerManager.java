package dev.nightbeam.odysseymap.marker;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MarkerManager {
    private static final MarkerManager INSTANCE = new MarkerManager();

    private final List<Marker> waypoints = new CopyOnWriteArrayList<>();
    private final Map<UUID, Marker> sessionMarkers = new HashMap<>();
    private Marker deathMarker;
    private Marker bedMarker;

    public static MarkerManager get() { return INSTANCE; }

    public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        updatePlayerMarkers(mc);
        if (OdysseyConfig.SHOW_ENTITIES.get()) {
            updateEntityMarkers(mc);
        }
    }

    private void updatePlayerMarkers(Minecraft mc) {
        sessionMarkers.entrySet().removeIf(e -> e.getValue().getType() == MarkerType.PLAYER);
        if (!OdysseyConfig.SHOW_PLAYERS.get()) return;
        ClientLevel level = mc.level;
        LocalPlayer self = mc.player;
        for (AbstractClientPlayer player : level.players()) {
            if (player == self) continue;
            Marker m = new Marker(player.getUUID(), MarkerType.PLAYER, level.dimension(),
                    player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ(),
                    player.getName().getString(), MarkerType.PLAYER.defaultColor);
            sessionMarkers.put(player.getUUID(), m);
        }
    }

    private void updateEntityMarkers(Minecraft mc) {
        sessionMarkers.entrySet().removeIf(e -> e.getValue().getType() == MarkerType.ENTITY);
        ClientLevel level = mc.level;
        LocalPlayer self = mc.player;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == self || entity instanceof AbstractClientPlayer) continue;
            if (self.distanceTo(entity) > 64) continue;
            Marker m = new Marker(entity.getUUID(), MarkerType.ENTITY, level.dimension(),
                    entity.blockPosition().getX(), entity.blockPosition().getY(), entity.blockPosition().getZ(),
                    entity.getType().getDescription().getString(), MarkerType.ENTITY.defaultColor);
            sessionMarkers.put(entity.getUUID(), m);
        }
    }

    public Marker addWaypoint(ResourceKey<Level> dimension, int x, int z, String label) {
        return addWaypoint(dimension, x, Marker.UNKNOWN_Y, z, label, MarkerType.WAYPOINT.defaultColor);
    }

    public Marker addWaypoint(ResourceKey<Level> dimension, int x, int y, int z, String label, int color) {
        Marker marker = new Marker(UUID.randomUUID(), MarkerType.WAYPOINT, dimension, x, y, z, label, color);
        waypoints.add(marker);
        MarkerStorage.save();
        return marker;
    }

    public void updateWaypoint(UUID id, String label, int color, int x, int y, int z) {
        for (Marker marker : waypoints) {
            if (marker.getId().equals(id)) {
                marker.setLabel(label);
                marker.setColor(color);
                marker.setX(x);
                marker.setY(y);
                marker.setZ(z);
                MarkerStorage.save();
                return;
            }
        }
    }

    public Marker getWaypoint(UUID id) {
        for (Marker marker : waypoints) {
            if (marker.getId().equals(id)) return marker;
        }
        return null;
    }

    public void removeWaypoint(UUID id) {
        waypoints.removeIf(m -> m.getId().equals(id));
        MarkerStorage.save();
    }

    public void toggleFavorite(UUID id) {
        Marker marker = getWaypoint(id);
        if (marker != null) {
            marker.setFavorite(!marker.isFavorite());
            MarkerStorage.save();
        }
    }

    public void toggleVisibility(UUID id) {
        Marker marker = getWaypoint(id);
        if (marker != null) {
            marker.setVisible(!marker.isVisible());
            MarkerStorage.save();
        }
    }

    public void setDeathPoint(ResourceKey<Level> dimension, int x, int z) {
        if (!OdysseyConfig.SHOW_DEATH.get()) return;
        deathMarker = new Marker(UUID.randomUUID(), MarkerType.DEATH, dimension, x, Marker.UNKNOWN_Y, z, "Death",
                MarkerType.DEATH.defaultColor);
    }

    public void setBedPoint(ResourceKey<Level> dimension, int x, int z) {
        if (!OdysseyConfig.SHOW_BEDS.get()) return;
        bedMarker = new Marker(UUID.randomUUID(), MarkerType.BED, dimension, x, Marker.UNKNOWN_Y, z, "Bed",
                MarkerType.BED.defaultColor);
    }

    public List<Marker> getVisibleMarkers(ResourceKey<Level> dimension) {
        List<Marker> all = new ArrayList<>();
        if (OdysseyConfig.SHOW_WAYPOINTS.get()) {
            waypoints.stream().filter(m -> m.getDimension().equals(dimension) && m.isVisible()).forEach(all::add);
        }
        if (deathMarker != null && deathMarker.getDimension().equals(dimension) && OdysseyConfig.SHOW_DEATH.get()) {
            all.add(deathMarker);
        }
        if (bedMarker != null && bedMarker.getDimension().equals(dimension) && OdysseyConfig.SHOW_BEDS.get()) {
            all.add(bedMarker);
        }
        sessionMarkers.values().stream()
                .filter(m -> m.getDimension().equals(dimension) && isTypeVisible(m.getType()))
                .forEach(all::add);
        return all;
    }

    public List<Marker> getWaypointsInDimension(ResourceKey<Level> dimension) {
        return waypoints.stream()
                .filter(m -> m.getDimension().equals(dimension) && m.isVisible())
                .toList();
    }

    private boolean isTypeVisible(MarkerType type) {
        return switch (type) {
            case PLAYER -> OdysseyConfig.SHOW_PLAYERS.get();
            case ENTITY -> OdysseyConfig.SHOW_ENTITIES.get();
            case PORTAL -> OdysseyConfig.SHOW_PORTALS.get();
            case STRUCTURE -> OdysseyConfig.SHOW_STRUCTURES.get();
            default -> true;
        };
    }

    public List<Marker> getWaypoints() { return Collections.unmodifiableList(waypoints); }
    public void clearWaypoints() { waypoints.clear(); }
    public void addWaypointDirect(Marker marker) { waypoints.add(marker); }
    public void clearSession() { sessionMarkers.clear(); deathMarker = null; }
}
