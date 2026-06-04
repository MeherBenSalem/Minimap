package dev.nightbeam.odysseymap.config;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockOverrideConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OVERRIDES;

    private static Map<BlockState, MapColor> resolvedOverrides = Collections.emptyMap();

    private static final HolderLookup.RegistryLookup<Block> BLOCK_LOOKUP =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).lookupOrThrow(Registries.BLOCK);

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("blocks");
        OVERRIDES = builder.comment(
                "Block color overrides as \"blockstate=mapColorId\" or \"blockstate=otherBlockState\"",
                "Example: minecraft:note_block[instrument=harp,note=0,powered=false]=minecraft:obsidian"
        ).defineList("overrides", List.of(), o -> o instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    public static void reload() {
        Map<BlockState, MapColor> map = new HashMap<>();
        for (String entry : OVERRIDES.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = entry.substring(0, eq).trim();
            String value = entry.substring(eq + 1).trim();
            try {
                BlockStateParser.BlockResult block = BlockStateParser.parseForBlock(BLOCK_LOOKUP, new StringReader(key), false);
                MapColor color;
                try {
                    color = MapColor.byId(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    BlockStateParser.BlockResult mapping = BlockStateParser.parseForBlock(BLOCK_LOOKUP, new StringReader(value), false);
                    color = mapping.blockState().getMapColor(null, null);
                }
                map.put(block.blockState(), color);
            } catch (CommandSyntaxException e) {
                dev.nightbeam.odysseymap.OdysseyMap.LOGGER.warn("Invalid block override: {}", entry);
            }
        }
        resolvedOverrides = Map.copyOf(map);
    }

    public static Map<BlockState, MapColor> getResolvedOverrides() {
        return resolvedOverrides;
    }
}
