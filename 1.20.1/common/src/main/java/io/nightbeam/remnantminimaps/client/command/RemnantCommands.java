package io.nightbeam.remnantminimaps.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.nightbeam.remnantminimaps.client.preload.MapPreloader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side commands. Each loader registers them on its own client command dispatcher, so the
 * tree is built generically for any command source type.
 *
 * <pre>
 * /remnantmap preload &lt;radius&gt;   fill in the map around you from the save (radius in blocks)
 * /remnantmap preload status     show progress
 * /remnantmap preload cancel     stop
 * </pre>
 */
public final class RemnantCommands {
    public static final String ROOT = "remnantmap";
    /** The command accepts radii up to this; the configured limit applies on top. */
    public static final int MAX_RADIUS_ARGUMENT = 4096;

    private RemnantCommands() {}

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal(ROOT)
                .then(LiteralArgumentBuilder.<S>literal("preload")
                        .executes(context -> reply(MapPreloader.status()))
                        .then(RequiredArgumentBuilder.<S, Integer>argument("radius", IntegerArgumentType.integer(16, MAX_RADIUS_ARGUMENT))
                                .executes(context -> reply(MapPreloader.start(IntegerArgumentType.getInteger(context, "radius")))))
                        .then(LiteralArgumentBuilder.<S>literal("status")
                                .executes(context -> reply(MapPreloader.status())))
                        .then(LiteralArgumentBuilder.<S>literal("cancel")
                                .executes(context -> reply(MapPreloader.cancel())))));
    }

    private static int reply(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
        return 1;
    }
}
