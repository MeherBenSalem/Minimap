package dev.nightbeam.odysseymap.config;

import com.google.gson.*;
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
import dev.nightbeam.odysseymap.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BlockOverrideConfig {
    private static final Logger LOG = LoggerFactory.getLogger("OdysseyMap");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final ConfigValue<List<String>> OVERRIDES =
            new ConfigValue<>("overrides", "Block color overrides: blockstate=mapColorId or blockstate=otherBlockState", Collections.emptyList());

    private static Map<BlockState, MapColor> resolvedOverrides = Collections.emptyMap();

    private static final HolderLookup.RegistryLookup<Block> BLOCK_LOOKUP =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).lookupOrThrow(Registries.BLOCK);

    public static void reload() {
        load();
        Map<BlockState, MapColor> map = new HashMap<>();
        for (String entry : OVERRIDES.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
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
                LOG.warn("Invalid block override: {}", entry);
            }
        }
        resolvedOverrides = Map.copyOf(map);
    }

    @SuppressWarnings("unchecked")
    public static void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            if (root.has("overrides") && root.get("overrides").isJsonArray()) {
                List<String> list = new ArrayList<>();
                for (JsonElement e : root.getAsJsonArray("overrides")) {
                    list.add(e.getAsString());
                }
                OVERRIDES.set(list);
            }
        } catch (IOException e) {
            LOG.error("Failed to load block overrides", e);
        }
    }

    public static void save() {
        Path path = getConfigPath();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overrides", OVERRIDES.get());
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOG.error("Failed to save block overrides", e);
        }
    }

    private static Path getConfigPath() {
        return Services.PLATFORM.getConfigDir().resolve("odysseymap").resolve("blocks.json");
    }

    public static Map<BlockState, MapColor> getResolvedOverrides() {
        return resolvedOverrides;
    }
}
