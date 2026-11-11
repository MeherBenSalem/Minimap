package io.nightbeam.remnantminimaps.network;

import io.nightbeam.remnantminimaps.RemnantMinimaps;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Network messages of the optional server side. Both are tiny and only travel between a client and
 * a server that both have the mod; everyone else never sees them.
 */
public final class MapTeleportPayloads {
    /** Sent as {@code yHint} when the client does not know the column's height. */
    public static final int UNKNOWN_Y = Integer.MIN_VALUE;

    private MapTeleportPayloads() {}

    /** Client to server: teleport me to this column of the map I am looking at. */
    public record Request(String dimension, int x, int z, int yHint) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(RemnantMinimaps.id("map_teleport"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Request::dimension,
                ByteBufCodecs.INT, Request::x,
                ByteBufCodecs.INT, Request::z,
                ByteBufCodecs.INT, Request::yHint,
                Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** Server to client: whether this player may teleport from the map right now. */
    public record Access(boolean allowed) implements CustomPacketPayload {
        public static final Type<Access> TYPE = new Type<>(RemnantMinimaps.id("map_teleport_access"));
        public static final StreamCodec<ByteBuf, Access> CODEC = ByteBufCodecs.BOOL.map(Access::new, Access::allowed);

        @Override
        public Type<Access> type() {
            return TYPE;
        }
    }
}
