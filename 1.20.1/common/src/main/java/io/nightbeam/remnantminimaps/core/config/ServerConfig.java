package io.nightbeam.remnantminimaps.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings the server owner controls, persisted as {@code config/remnantminimaps-server.json} on the
 * server (in single-player, in the game's own config folder). Missing fields keep their defaults.
 * Read when the server starts.
 */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String FILE_NAME = "remnantminimaps-server.json";

    public MapTeleport mapTeleport = new MapTeleport();

    /** Teleporting by clicking the world map. */
    public static final class MapTeleport {
        /** Master switch. When false nobody can teleport from the map. */
        public boolean enabled = true;
        /**
         * Operator level needed, 0 to 4. 2 is what {@code /tp} needs. In single-player and LAN
         * worlds cheats are always required as well. Permission mods can override this through the
         * {@code remnantminimaps.map_teleport} node where the loader has a permission API.
         */
        public int permissionLevel = 2;
        /** Seconds a player must wait between two map teleports, 0 to 3600. */
        public int cooldownSeconds = 3;
        /** Allow targets in chunks that were never generated (loading them generates terrain). */
        public boolean allowUngeneratedChunks = false;
        /** How far around the clicked block to look for a safe place to land, 0 to 8 blocks. */
        public int landingSearchRadius = 3;
    }

    public void validate() {
        if (mapTeleport == null) {
            mapTeleport = new MapTeleport();
        }
        mapTeleport.permissionLevel = clamp(mapTeleport.permissionLevel, 0, 4);
        mapTeleport.cooldownSeconds = clamp(mapTeleport.cooldownSeconds, 0, 3600);
        mapTeleport.landingSearchRadius = clamp(mapTeleport.landingSearchRadius, 0, 8);
    }

    /** Result of {@link #load}: the settings, and a problem to report when the file was unreadable. */
    public record Loaded(ServerConfig config, String problem) {}

    /**
     * Reads the file, writing it with defaults when it does not exist yet. A broken file is left
     * untouched (so the owner can fix it) and the defaults are used.
     */
    public static Loaded load(Path file) {
        ServerConfig config = null;
        String problem = null;
        boolean exists = Files.isRegularFile(file);
        if (exists) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, ServerConfig.class);
            } catch (IOException | JsonParseException e) {
                problem = e.getMessage();
            }
        }
        if (config == null) {
            config = new ServerConfig();
        }
        config.validate();
        if (!exists) {
            try {
                config.save(file);
            } catch (IOException e) {
                problem = "could not write defaults: " + e.getMessage();
            }
        }
        return new Loaded(config, problem);
    }

    public void save(Path file) throws IOException {
        validate();
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
