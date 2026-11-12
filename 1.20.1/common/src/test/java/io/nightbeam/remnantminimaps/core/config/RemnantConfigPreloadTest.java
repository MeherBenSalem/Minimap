package io.nightbeam.remnantminimaps.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemnantConfigPreloadTest {
    @TempDir
    Path dir;

    @Test
    void defaults() {
        RemnantConfig config = new RemnantConfig();
        assertEquals(1024, config.preloadMaxRadius);
        assertEquals(8, config.preloadChunksPerTick);
    }

    @Test
    void limitsAreClampedToTheirRanges() throws IOException {
        Path file = dir.resolve("remnantminimaps.json");
        Files.writeString(file, "{\"preloadMaxRadius\": 100000, \"preloadChunksPerTick\": 0}", StandardCharsets.UTF_8);
        RemnantConfig config = RemnantConfig.load(file);
        assertEquals(4096, config.preloadMaxRadius);
        assertEquals(1, config.preloadChunksPerTick);

        Files.writeString(file, "{\"preloadMaxRadius\": 1, \"preloadChunksPerTick\": 500}", StandardCharsets.UTF_8);
        config = RemnantConfig.load(file);
        assertEquals(64, config.preloadMaxRadius);
        assertEquals(32, config.preloadChunksPerTick);
    }

    @Test
    void olderConfigFilesGetTheDefaults() throws IOException {
        Path file = dir.resolve("remnantminimaps.json");
        Files.writeString(file, "{\"minimapSize\": 128}", StandardCharsets.UTF_8);
        RemnantConfig config = RemnantConfig.load(file);
        assertEquals(128, config.minimapSize);
        assertEquals(1024, config.preloadMaxRadius);
        assertEquals(8, config.preloadChunksPerTick);
    }
}
