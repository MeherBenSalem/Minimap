package io.nightbeam.remnantminimaps.network;

import io.nightbeam.remnantminimaps.RemnantMinimaps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Network messages of the optional server side. Both are tiny and only travel between a client and
 * a server that both have the mod; everyone else never sees them.
 */
public final class MapTeleportPayloads {
    /** Sent as {@code yHint} when the client does not know the column's height. */
    public static final int UNKNOWN_Y = Integer.MIN_VALUE;
    /** Fabric channel of {@link Request}; on Forge, the id of the mod's channel. */
    public static final ResourceLocation REQUEST_ID = RemnantMinimaps.id("map_teleport");
    /** Fabric channel of {@link Access}. */
    public static final ResourceLocation ACCESS_ID = RemnantMinimaps.id("map_teleport_access");

    private MapTeleportPayloads() {}

    /** Client to server: teleport me to this column of the map I am looking at. */
    public record Request(String dimension, int x, int z, int yHint) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(dimension, 256);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeInt(yHint);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUtf(256), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    /** Server to client: whether this player may teleport from the map right now. */
    public record Access(boolean allowed) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(allowed);
        }

        public static Access decode(FriendlyByteBuf buf) {
            return new Access(buf.readBoolean());
        }
    }
}
