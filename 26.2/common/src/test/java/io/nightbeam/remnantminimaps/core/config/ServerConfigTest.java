package io.nightbeam.remnantminimaps.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {
    @TempDir
    Path dir;

    @Test
    void writesDefaultsWhenMissing() {
        Path file = dir.resolve("config").resolve(ServerConfig.FILE_NAME);
        ServerConfig.Loaded loaded = ServerConfig.load(file);
        assertNull(loaded.problem());
        assertTrue(Files.isRegularFile(file));
        ServerConfig.MapTeleport t = loaded.config().mapTeleport;
        assertTrue(t.enabled);
        assertEquals(2, t.permissionLevel);
        assertEquals(3, t.cooldownSeconds);
        assertFalse(t.allowUngeneratedChunks);
        assertEquals(3, t.landingSearchRadius);
    }

    @Test
    void clampsOutOfRangeValuesAndKeepsMissingOnesAtDefault() throws IOException {
        Path file = dir.resolve(ServerConfig.FILE_NAME);
        Files.writeString(file, "{\"mapTeleport\":{\"enabled\":false,\"permissionLevel\":9,\"cooldownSeconds\":-5,\"landingSearchRadius\":50}}",
                StandardCharsets.UTF_8);
        ServerConfig.MapTeleport t = ServerConfig.load(file).config().mapTeleport;
        assertFalse(t.enabled);
        assertEquals(4, t.permissionLevel);
        assertEquals(0, t.cooldownSeconds);
        assertEquals(8, t.landingSearchRadius);
        assertFalse(t.allowUngeneratedChunks);
    }

    @Test
    void brokenFileIsReportedAndLeftAlone() throws IOException {
        Path file = dir.resolve(ServerConfig.FILE_NAME);
        String broken = "{ this is not json";
        Files.writeString(file, broken, StandardCharsets.UTF_8);
        ServerConfig.Loaded loaded = ServerConfig.load(file);
        assertNotNull(loaded.problem());
        assertTrue(loaded.config().mapTeleport.enabled, "defaults are used");
        assertEquals(broken, Files.readString(file, StandardCharsets.UTF_8), "the owner's file is not overwritten");
    }

    @Test
    void nullSectionFallsBackToDefaults() throws IOException {
        Path file = dir.resolve(ServerConfig.FILE_NAME);
        Files.writeString(file, "{\"mapTeleport\":null}", StandardCharsets.UTF_8);
        assertEquals(2, ServerConfig.load(file).config().mapTeleport.permissionLevel);
    }
}
