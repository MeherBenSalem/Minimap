package dev.nightbeam.odysseymap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nightbeam.odysseymap.client.ClientEvents;
import dev.nightbeam.odysseymap.client.OdysseyKeyMappings;
import dev.nightbeam.odysseymap.render.MinimapHudRenderer;
import dev.nightbeam.odysseymap.render.WorldWaypointRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@Mod(OdysseyMapCommon.MOD_ID)
public class OdysseyMap {

    private static final ResourceLocation MINIMAP_LAYER =
            ResourceLocation.fromNamespaceAndPath(OdysseyMapCommon.MOD_ID, "minimap");

    private static KeyMapping TOGGLE_MINIMAP;
    private static KeyMapping OPEN_FULLSCREEN;
    private static KeyMapping ZOOM_IN;
    private static KeyMapping ZOOM_OUT;
    private static KeyMapping CREATE_WAYPOINT;
    private static KeyMapping OPEN_SETTINGS;

    private final ClientEvents events = new ClientEvents();
    private boolean wasDead;
    private boolean wasSleeping;

    public OdysseyMap(IEventBus modEventBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            OdysseyMapCommon.init();
            modEventBus.addListener(this::onRegisterKeys);
            modEventBus.addListener(this::onRegisterOverlays);
            NeoForge.EVENT_BUS.register(this);
        }
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        TOGGLE_MINIMAP = new KeyMapping("key.odysseymap.toggle",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_TOGGLE_MINIMAP, OdysseyKeyMappings.CATEGORY);
        OPEN_FULLSCREEN = new KeyMapping("key.odysseymap.fullscreen",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_OPEN_FULLSCREEN, OdysseyKeyMappings.CATEGORY);
        ZOOM_IN = new KeyMapping("key.odysseymap.zoom_in",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_ZOOM_IN, OdysseyKeyMappings.CATEGORY);
        ZOOM_OUT = new KeyMapping("key.odysseymap.zoom_out",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_ZOOM_OUT, OdysseyKeyMappings.CATEGORY);
        CREATE_WAYPOINT = new KeyMapping("key.odysseymap.waypoint",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_CREATE_WAYPOINT, OdysseyKeyMappings.CATEGORY);
        OPEN_SETTINGS = new KeyMapping("key.odysseymap.settings",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_OPEN_SETTINGS, OdysseyKeyMappings.CATEGORY);
        event.register(TOGGLE_MINIMAP);
        event.register(OPEN_FULLSCREEN);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(CREATE_WAYPOINT);
        event.register(OPEN_SETTINGS);
    }

    private void onRegisterOverlays(RegisterGuiLayersEvent event) {
        event.registerAboveAll(MINIMAP_LAYER, (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() == null) return;
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            MinimapHudRenderer.render(graphics, partialTick,
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
        });
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        events.onClientTick(mc);

        LocalPlayer player = mc.player;
        if (player != null) {
            if (wasDead && !player.isDeadOrDying()) {
                events.onPlayerDeath(player);
            }
            wasDead = player.isDeadOrDying();

            if (player.isSleeping() && !wasSleeping) {
                player.getSleepingPos().ifPresent(bedPos -> events.onSleep(player, bedPos));
            }
            wasSleeping = player.isSleeping();
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (TOGGLE_MINIMAP.consumeClick()) events.onToggleMinimap();
        while (OPEN_FULLSCREEN.consumeClick()) events.onOpenFullscreen(mc);
        while (ZOOM_IN.consumeClick()) events.onZoomIn();
        while (ZOOM_OUT.consumeClick()) events.onZoomOut();
        while (CREATE_WAYPOINT.consumeClick()) events.onCreateWaypoint(mc);
        while (OPEN_SETTINGS.consumeClick()) events.onOpenSettings(mc);
    }

    @SubscribeEvent
    public void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        events.onPlayerLogout();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) events.onLevelUnload();
    }

    @SubscribeEvent
    public void onPlayerRespawn(ClientPlayerNetworkEvent.Clone event) {
        if (event.getOldPlayer().isDeadOrDying()) {
            events.onPlayerDeath(event.getNewPlayer());
        }
    }

    @SubscribeEvent
    public void onBlockChange(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide()) {
            var pos = event.getPos();
            events.onBlockChange(Minecraft.getInstance(), pos.getX(), pos.getZ());
        }
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        WorldWaypointRenderer.render(
                event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.font, event.getCamera(), mc.level,
                event.getCamera().getPosition(), partialTick, mc.level.getGameTime());
    }
}
