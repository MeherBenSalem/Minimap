package io.nightbeam.remnantminimaps.client;

import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;

/**
 * Client half of map teleport: remembers whether the server lets this player teleport from the map
 * and sends requests. The server decides everything; this only hides the action when it would be
 * refused anyway.
 */
public final class MapTeleportClient {
    /** What the loader provides: whether the server has the channel, and sending. */
    public interface Hooks {
        boolean serverSupports();

        void send(MapTeleportPayloads.Request request);
    }

    private static Hooks hooks;
    private static volatile boolean allowed;

    private MapTeleportClient() {}

    public static void init(Hooks loaderHooks) {
        hooks = loaderHooks;
    }

    /** Called with the server's answer, on the client thread. */
    public static void onAccess(boolean value) {
        allowed = value;
    }

    public static void reset() {
        allowed = false;
    }

    /** True when the server has the mod and currently allows this player to teleport from the map. */
    public static boolean available() {
        return allowed && hooks != null && hooks.serverSupports();
    }

    public static void request(String dimension, int x, int z, int yHint) {
        if (available()) {
            hooks.send(new MapTeleportPayloads.Request(dimension, x, z, yHint));
        }
    }
}
