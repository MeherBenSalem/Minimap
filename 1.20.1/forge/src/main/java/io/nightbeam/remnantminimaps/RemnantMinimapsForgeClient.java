package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.client.Keys;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.screen.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-side wiring for Forge: key mappings, the HUD overlay, ticking, chunk loads and world rendering. */
final class RemnantMinimapsForgeClient {
    private RemnantMinimapsForgeClient() {}

    static void init(IEventBus modBus) {
        RemnantClient.init();
        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class,
                event -> Keys.ALL.forEach(event::register));
        modBus.addListener(EventPriority.NORMAL, false, RegisterGuiOverlaysEvent.class, event ->
                event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), "minimap",
                        (gui, graphics, partialTick, width, height) -> RemnantClient.renderHud(graphics, partialTick)));
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));

        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                RemnantClient.onClientTick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ChunkEvent.Load.class, event -> {
            if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
                RemnantClient.onChunkLoaded(level, chunk);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingOut.class,
                event -> RemnantClient.onDisconnect());
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                RemnantClient.renderWorld(event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(),
                        event.getCamera(), event.getPartialTick());
            }
        });
    }
}
