package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.client.Keys;
import io.nightbeam.remnantminimaps.client.MapTeleportClient;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.FriendlyByteBuf;

public class RemnantMinimapsFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RemnantClient.init();
        for (KeyMapping key : Keys.ALL) {
            KeyBindingHelper.registerKeyBinding(key);
        }
        ClientTickEvents.END_CLIENT_TICK.register(RemnantClient::onClientTick);
        ClientChunkEvents.CHUNK_LOAD.register(RemnantClient::onChunkLoaded);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RemnantClient.onDisconnect());
        ClientPlayNetworking.registerGlobalReceiver(MapTeleportPayloads.ACCESS_ID, (client, handler, buf, sender) -> {
            boolean allowed = MapTeleportPayloads.Access.decode(buf).allowed();
            client.execute(() -> MapTeleportClient.onAccess(allowed));
        });
        MapTeleportClient.init(new MapTeleportClient.Hooks() {
            @Override
            public boolean serverSupports() {
                return ClientPlayNetworking.canSend(MapTeleportPayloads.REQUEST_ID);
            }

            @Override
            public void send(MapTeleportPayloads.Request request) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                request.encode(buf);
                ClientPlayNetworking.send(MapTeleportPayloads.REQUEST_ID, buf);
            }
        });
        // The HUD is drawn from GuiMixin, just below the chat like on the other loaders.
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.matrixStack() != null && context.consumers() != null) {
                RemnantClient.renderWorld(context.matrixStack(), context.consumers(), context.camera(), context.tickDelta());
            }
        });
    }
}
