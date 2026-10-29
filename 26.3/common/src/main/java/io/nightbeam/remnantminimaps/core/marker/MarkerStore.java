package io.nightbeam.remnantminimaps.core.marker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** All markers of one world, across dimensions, persisted as {@code markers.json}. */
public final class MarkerStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final class FileModel {
        int version = 1;
        List<Marker> markers = new ArrayList<>();
    }

    private final Path file;
    private final List<Marker> markers = new ArrayList<>();
    private int revision;
    private boolean dirty;
    private Consumer<Exception> errorHandler = e -> {};

    public MarkerStore(Path file) {
        this.file = file;
    }

    public void onError(Consumer<Exception> handler) {
        this.errorHandler = handler;
    }

    public void load() {
        markers.clear();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                FileModel model = GSON.fromJson(reader, FileModel.class);
                if (model != null && model.markers != null) {
                    for (Marker m : model.markers) {
                        if (m != null && m.id != null && m.dimension != null) {
                            if (m.name == null) {
                                m.name = "";
                            }
                            markers.add(m);
                        }
                    }
                }
            } catch (IOException | JsonParseException e) {
                errorHandler.accept(e);
            }
        }
        revision++;
    }

    /** Writes pending changes on the given executor. Returns immediately. */
    public void saveIfDirty(Executor executor) {
        if (!dirty) {
            return;
        }
        dirty = false;
        FileModel model = new FileModel();
        for (Marker m : markers) {
            model.markers.add(m.copy());
        }
        executor.execute(() -> write(model));
    }

    public void saveNow() {
        if (!dirty) {
            return;
        }
        dirty = false;
        FileModel model = new FileModel();
        model.markers.addAll(markers);
        write(model);
    }

    private void write(FileModel model) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(model, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            errorHandler.accept(e);
        }
    }

    /** Increments whenever markers change, so views can cache sorted lists. */
    public int revision() {
        return revision;
    }

    public void changed() {
        dirty = true;
        revision++;
    }

    public List<Marker> all() {
        return Collections.unmodifiableList(markers);
    }

    public List<Marker> inDimension(String dimension) {
        List<Marker> out = new ArrayList<>();
        for (Marker m : markers) {
            if (m.dimension.equals(dimension)) {
                out.add(m);
            }
        }
        return out;
    }

    public Marker byId(String id) {
        for (Marker m : markers) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public void add(Marker marker) {
        markers.add(marker);
        changed();
    }

    public void remove(Marker marker) {
        if (markers.removeIf(m -> m.id.equals(marker.id))) {
            changed();
        }
    }

    /** Replaces the stored marker with the same id, or adds it. */
    public void put(Marker marker) {
        for (int i = 0; i < markers.size(); i++) {
            if (markers.get(i).id.equals(marker.id)) {
                markers.set(i, marker);
                changed();
                return;
            }
        }
        add(marker);
    }

    public Marker beacon(String dimension) {
        for (Marker m : markers) {
            if (m.kind() == Marker.Kind.BEACON && m.dimension.equals(dimension)) {
                return m;
            }
        }
        return null;
    }

    public Marker setBeacon(String dimension, int x, int z, String name) {
        Marker beacon = beacon(dimension);
        if (beacon == null) {
            beacon = new Marker(name, MarkerIcon.BEACON, dimension, x, 0, z, false);
            beacon.kind = Marker.Kind.BEACON;
            beacon.showBeam = true;
            markers.add(beacon);
        } else {
            beacon.x = x;
            beacon.z = z;
            beacon.hasY = false;
        }
        changed();
        return beacon;
    }

    public void clearBeacon(String dimension) {
        if (markers.removeIf(m -> m.kind() == Marker.Kind.BEACON && m.dimension.equals(dimension))) {
            changed();
        }
    }

    public void addDeath(String dimension, int x, int y, int z, String name, int limit) {
        Marker death = new Marker(name, MarkerIcon.DEATH, dimension, x, y, z, true);
        death.kind = Marker.Kind.DEATH;
        death.showBeam = true;
        markers.add(death);
        List<Marker> deaths = new ArrayList<>();
        for (Marker m : markers) {
            if (m.kind() == Marker.Kind.DEATH) {
                deaths.add(m);
            }
        }
        deaths.sort(Comparator.comparingLong(m -> m.created));
        int excess = deaths.size() - Math.max(1, limit);
        for (int i = 0; i < excess; i++) {
            Marker old = deaths.get(i);
            markers.removeIf(m -> m.id.equals(old.id));
        }
        changed();
    }

    public void setBed(String dimension, int x, int y, int z, String name) {
        Marker bed = null;
        for (Marker m : markers) {
            if (m.kind() == Marker.Kind.BED) {
                bed = m;
                break;
            }
        }
        if (bed != null && bed.dimension.equals(dimension) && bed.x == x && bed.y == y && bed.z == z) {
            return;
        }
        if (bed == null) {
            bed = new Marker(name, MarkerIcon.BED, dimension, x, y, z, true);
            bed.kind = Marker.Kind.BED;
            markers.add(bed);
        } else {
            bed.dimension = dimension;
            bed.x = x;
            bed.y = y;
            bed.z = z;
            bed.hasY = true;
        }
        changed();
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public String toString() {
        return "MarkerStore[" + Objects.toString(file) + ", " + markers.size() + " markers]";
    }
}
