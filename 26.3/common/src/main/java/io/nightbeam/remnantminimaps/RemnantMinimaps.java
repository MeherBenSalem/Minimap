package io.nightbeam.remnantminimaps;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RemnantMinimaps {
    public static final String MOD_ID = "remnantminimaps";
    public static final String MOD_NAME = "Remnants Minimap";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private RemnantMinimaps() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
