package dev.nightbeam.odysseymap.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class OdysseyKeyMappings {
    public static final String CATEGORY = "key.categories.odysseymap";

    public static final int KEY_TOGGLE_MINIMAP = GLFW.GLFW_KEY_M;
    public static final int KEY_OPEN_FULLSCREEN = GLFW.GLFW_KEY_J;
    public static final int KEY_ZOOM_IN = GLFW.GLFW_KEY_EQUAL;
    public static final int KEY_ZOOM_OUT = GLFW.GLFW_KEY_MINUS;
    public static final int KEY_CREATE_WAYPOINT = GLFW.GLFW_KEY_B;

    private OdysseyKeyMappings() {}
}
