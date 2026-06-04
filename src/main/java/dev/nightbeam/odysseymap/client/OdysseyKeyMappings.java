package dev.nightbeam.odysseymap.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nightbeam.odysseymap.OdysseyMap;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class OdysseyKeyMappings {
    public static final String CATEGORY = "key.categories." + OdysseyMap.MOD_ID;

    public static KeyMapping TOGGLE_MINIMAP;
    public static KeyMapping OPEN_FULLSCREEN;
    public static KeyMapping ZOOM_IN;
    public static KeyMapping ZOOM_OUT;
    public static KeyMapping CREATE_WAYPOINT;

    private OdysseyKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        TOGGLE_MINIMAP = new KeyMapping(
                "key.odysseymap.toggle",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                CATEGORY
        );
        OPEN_FULLSCREEN = new KeyMapping(
                "key.odysseymap.fullscreen",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        );
        ZOOM_IN = new KeyMapping(
                "key.odysseymap.zoom_in",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,
                CATEGORY
        );
        ZOOM_OUT = new KeyMapping(
                "key.odysseymap.zoom_out",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                CATEGORY
        );
        CREATE_WAYPOINT = new KeyMapping(
                "key.odysseymap.waypoint",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        );

        event.register(TOGGLE_MINIMAP);
        event.register(OPEN_FULLSCREEN);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(CREATE_WAYPOINT);
    }
}
