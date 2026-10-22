package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.client.Keys;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.screen.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

@Mod(value = RemnantMinimaps.MOD_ID, dist = Dist.CLIENT)
public class RemnantMinimapsNeoForge {
    public RemnantMinimapsNeoForge(IEventBus modBus, ModContainer container) {
        RemnantClient.init();
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> Keys.ALL.forEach(event::register));
        modBus.addListener(RegisterGuiLayersEvent.class, event ->
                event.registerBelow(VanillaGuiLayers.CHAT, RemnantMinimaps.id("minimap"),
                        (graphics, deltaTracker) -> RemnantClient.renderHud(graphics, deltaTracker.getGameTimeDeltaPartialTick(false))));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> RemnantClient.onClientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, event -> {
            if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
                RemnantClient.onChunkLoaded(level, chunk);
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> RemnantClient.onDisconnect());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                RemnantClient.renderWorld(event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(),
                        event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        });
    }
}
