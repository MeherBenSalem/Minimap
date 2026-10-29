package io.nightbeam.remnantminimaps.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.nightbeam.remnantminimaps.RemnantMinimaps;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLScancode;

import java.util.List;

/** Key mappings. Created in common code, registered by each loader. */
public final class Keys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(RemnantMinimaps.id("keys"));

    public static final KeyMapping OPEN_MAP = key("open_map", SDLScancode.SDL_SCANCODE_M);
    public static final KeyMapping TOGGLE_MINIMAP = key("toggle_minimap", SDLScancode.SDL_SCANCODE_N);
    public static final KeyMapping ZOOM_IN = key("zoom_in", SDLScancode.SDL_SCANCODE_EQUALS);
    public static final KeyMapping ZOOM_OUT = key("zoom_out", SDLScancode.SDL_SCANCODE_MINUS);
    public static final KeyMapping ADD_MARKER = key("add_marker", SDLScancode.SDL_SCANCODE_B);
    public static final KeyMapping TOGGLE_ROTATION = key("toggle_rotation", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping SETTINGS = key("settings", InputConstants.UNKNOWN.getValue());

    public static final List<KeyMapping> ALL = List.of(OPEN_MAP, TOGGLE_MINIMAP, ZOOM_IN, ZOOM_OUT, ADD_MARKER, TOGGLE_ROTATION, SETTINGS);

    private Keys() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key." + RemnantMinimaps.MOD_ID + "." + name, InputConstants.Type.KEYBOARD, code, CATEGORY);
    }
}
