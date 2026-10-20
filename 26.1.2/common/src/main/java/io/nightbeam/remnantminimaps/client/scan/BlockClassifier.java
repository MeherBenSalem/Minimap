package io.nightbeam.remnantminimaps.client.scan;

import io.nightbeam.remnantminimaps.core.map.Columns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import java.util.IdentityHashMap;
import java.util.Map;

/** Decides how each block state appears on the map. Results are cached per state. */
public final class BlockClassifier {
    private static final int TINT_NONE = 0;
    private static final int TINT_SOURCE = 1;
    private static final int TINT_WATER = 2;

    /** category | tint << 8 */
    private final Map<BlockState, Integer> cache = new IdentityHashMap<>();

    public void clear() {
        cache.clear();
    }

    /** Blocks the map looks straight through: air, glass, grass tufts, flowers. */
    public static boolean isSeeThrough(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        MapColor color = state.getMapColor(level, pos);
        if (color == MapColor.NONE) {
            return true;
        }
        if (state.is(Blocks.SNOW)) {
            return false;
        }
        if (state.is(BlockTags.CROPS)) {
            return false;
        }
        return (color == MapColor.PLANT || state.canBeReplaced() || state.is(BlockTags.FLOWERS))
                && state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean isWater(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && fluid.is(FluidTags.WATER);
    }

    public static boolean isLava(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && fluid.is(FluidTags.LAVA);
    }

    private int info(BlockState state) {
        Integer cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        int category;
        int tint = TINT_NONE;
        if (isWater(state)) {
            category = Columns.WATER;
            tint = TINT_WATER;
        } else if (isLava(state)) {
            category = Columns.LAVA;
        } else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET)) {
            category = Columns.GRASS;
            tint = TINT_SOURCE;
        } else if (state.is(BlockTags.LEAVES)) {
            category = Columns.FOLIAGE;
            tint = TINT_SOURCE;
        } else if (state.is(Blocks.DIRT_PATH)) {
            category = Columns.PATH;
        } else if (state.is(Blocks.FARMLAND) || state.is(BlockTags.CROPS)) {
            category = Columns.FIELD;
        } else if (state.is(BlockTags.SNOW) || state.is(BlockTags.ICE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
            category = Columns.SNOW;
        } else if (isBuilding(state)) {
            category = Columns.BUILDING;
        } else if (state.is(BlockTags.SAND) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)) {
            category = Columns.SAND;
        } else if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.CALCITE)) {
            category = Columns.STONE;
        } else if (state.is(Blocks.BEDROCK) || state.is(Blocks.END_STONE)) {
            category = Columns.VOID;
        } else {
            category = Columns.GROUND;
        }
        int value = category | (tint << 8);
        cache.put(state, value);
        return value;
    }

    private static boolean isBuilding(BlockState state) {
        return state.is(BlockTags.PLANKS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS)
                || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.BEDS)
                || state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)
                || state.is(Blocks.BRICKS) || state.is(Blocks.BOOKSHELF) || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.SMOOTH_STONE) || state.is(Blocks.POLISHED_ANDESITE);
    }

    /** Packed column color for the given surface state. */
    public int classify(BlockState state, ClientLevel level, BlockPos pos) {
        int info = info(state);
        int category = info & 0xFF;
        int tint = info >> 8;
        int rgb;
        if (tint == TINT_WATER) {
            rgb = level.getBiome(pos).value().getWaterColor();
        } else if (tint == TINT_SOURCE) {
            BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
            rgb = source != null ? source.colorInWorld(state, level, pos) : state.getMapColor(level, pos).col;
        } else {
            rgb = state.getMapColor(level, pos).col;
        }
        return Columns.pack(category, rgb & 0xFFFFFF);
    }
}
