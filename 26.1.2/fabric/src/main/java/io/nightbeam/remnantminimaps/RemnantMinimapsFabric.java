package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.client.Keys;
import io.nightbeam.remnantminimaps.client.MapTeleportClient;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;

public class RemnantMinimapsFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RemnantClient.init();
        for (KeyMapping key : Keys.ALL) {
            KeyMappingHelper.registerKeyMapping(key);
        }
        ClientTickEvents.END_CLIENT_TICK.register(RemnantClient::onClientTick);
        ClientChunkEvents.CHUNK_LOAD.register(RemnantClient::onChunkLoaded);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RemnantClient.onDisconnect());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, RemnantMinimaps.id("minimap"), RemnantClient::renderHud);
        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
                RemnantClient.submitWorld(context.poseStack(), context.submitNodeCollector(), context.levelState()));

        ClientPlayNetworking.registerGlobalReceiver(MapTeleportPayloads.Access.TYPE,
                (payload, context) -> context.client().execute(() -> MapTeleportClient.onAccess(payload.allowed())));
        MapTeleportClient.init(new MapTeleportClient.Hooks() {
            @Override
            public boolean serverSupports() {
                return ClientPlayNetworking.canSend(MapTeleportPayloads.Request.TYPE);
            }

            @Override
            public void send(MapTeleportPayloads.Request request) {
                ClientPlayNetworking.send(request);
            }
        });
    }
}
