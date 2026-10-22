package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.client.Keys;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;

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
        // The HUD is drawn from GuiMixin, just below the chat like on the other loaders.
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.matrixStack() != null && context.consumers() != null) {
                RemnantClient.renderWorld(context.matrixStack(), context.consumers(), context.camera(),
                        context.tickCounter().getGameTimeDeltaPartialTick(false));
            }
        });
    }
}
