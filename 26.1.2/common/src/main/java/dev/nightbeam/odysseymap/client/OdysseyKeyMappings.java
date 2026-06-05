package dev.nightbeam.odysseymap.client;

import dev.nightbeam.odysseymap.OdysseyMapCommon;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class OdysseyKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(OdysseyMapCommon.MOD_ID, "keys"));

    public static final int KEY_TOGGLE_MINIMAP = GLFW.GLFW_KEY_M;
    public static final int KEY_OPEN_FULLSCREEN = GLFW.GLFW_KEY_J;
    public static final int KEY_ZOOM_IN = GLFW.GLFW_KEY_EQUAL;
    public static final int KEY_ZOOM_OUT = GLFW.GLFW_KEY_MINUS;
    public static final int KEY_CREATE_WAYPOINT = GLFW.GLFW_KEY_B;

    private OdysseyKeyMappings() {}
}
