package dev.nightbeam.odysseymap.platform;
import java.nio.file.Path;
public class Services { public static Path configDir; public static final Platform PLATFORM = new Platform();
public static class Platform { public Path getConfigDir() { return configDir; } } }
