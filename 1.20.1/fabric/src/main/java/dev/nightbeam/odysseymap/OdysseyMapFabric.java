package dev.nightbeam.odysseymap;

import dev.nightbeam.odysseymap.client.ClientEvents;
import dev.nightbeam.odysseymap.client.OdysseyKeyMappings;
import dev.nightbeam.odysseymap.render.MinimapHudRenderer;
import dev.nightbeam.odysseymap.render.WorldWaypointRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;

public class OdysseyMapFabric implements ClientModInitializer {

    private static KeyMapping TOGGLE_MINIMAP;
    private static KeyMapping OPEN_FULLSCREEN;
    private static KeyMapping ZOOM_IN;
    private static KeyMapping ZOOM_OUT;
    private static KeyMapping CREATE_WAYPOINT;
    private static KeyMapping OPEN_SETTINGS;

    private final ClientEvents events = new ClientEvents();
    private boolean wasDead;
    private boolean wasSleeping;

    @Override
    public void onInitializeClient() {
        OdysseyMapCommon.init();

        registerKeyBindings();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            events.onClientTick(client);

            // Check key presses
            while (TOGGLE_MINIMAP.consumeClick()) events.onToggleMinimap();
            while (OPEN_FULLSCREEN.consumeClick()) events.onOpenFullscreen(client);
            while (ZOOM_IN.consumeClick()) events.onZoomIn();
            while (ZOOM_OUT.consumeClick()) events.onZoomOut();
            while (CREATE_WAYPOINT.consumeClick()) events.onCreateWaypoint(client);
            while (OPEN_SETTINGS.consumeClick()) events.onOpenSettings(client);

            // Death detection via tick
            LocalPlayer player = client.player;
            if (player != null) {
                if (wasDead && !player.isDeadOrDying()) {
                    events.onPlayerDeath(player);
                }
                wasDead = player.isDeadOrDying();

                // Sleep detection via tick
                if (player.isSleeping() && !wasSleeping) {
                    player.getSleepingPos().ifPresent(bedPos ->
                            events.onSleep(player, bedPos));
                }
                wasSleeping = player.isSleeping();
            }
        });

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null) {
                MinimapHudRenderer.render(graphics, tickDelta,
                        mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight());
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            WorldWaypointRenderer.render(
                    context.matrixStack(), mc.renderBuffers().bufferSource(),
                    mc.font, context.camera(), mc.level,
                    context.camera().getPosition(), context.tickDelta(),
                    mc.level.getGameTime());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            events.onPlayerLogout();
        });
    }

    private void registerKeyBindings() {
        TOGGLE_MINIMAP = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.odysseymap.toggle", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_TOGGLE_MINIMAP, OdysseyKeyMappings.CATEGORY));
        OPEN_FULLSCREEN = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.odysseymap.fullscreen", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_OPEN_FULLSCREEN, OdysseyKeyMappings.CATEGORY));
        ZOOM_IN = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.odysseymap.zoom_in", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_ZOOM_IN, OdysseyKeyMappings.CATEGORY));
        ZOOM_OUT = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.odysseymap.zoom_out", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_ZOOM_OUT, OdysseyKeyMappings.CATEGORY));
        CREATE_WAYPOINT = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.odysseymap.waypoint", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_CREATE_WAYPOINT, OdysseyKeyMappings.CATEGORY));
        OPEN_SETTINGS = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.odysseymap.settings", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_OPEN_SETTINGS, OdysseyKeyMappings.CATEGORY));
    }
}
