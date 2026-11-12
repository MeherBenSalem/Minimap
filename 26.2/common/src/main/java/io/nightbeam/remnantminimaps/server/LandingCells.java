package io.nightbeam.remnantminimaps.server;

import io.nightbeam.remnantminimaps.core.teleport.SafeLanding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/** Describes blocks of a loaded chunk to {@link SafeLanding}. Server thread only. */
final class LandingCells {
    private LandingCells() {}

    static SafeLanding.Cell cell(BlockState state, BlockGetter level, BlockPos pos) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            if (fluid.is(FluidTags.WATER)) {
                // Waterlogged stairs and slabs still hold you up.
                return state.getCollisionShape(level, pos).isEmpty() ? SafeLanding.Cell.WATER : SafeLanding.Cell.FLOOR;
            }
            return SafeLanding.Cell.DANGER; // lava and unknown modded fluids
        }
        if (isHarmful(state)) {
            return SafeLanding.Cell.DANGER;
        }
        return state.getCollisionShape(level, pos).isEmpty() ? SafeLanding.Cell.OPEN : SafeLanding.Cell.FLOOR;
    }

    private static boolean isHarmful(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)
                || state.is(Blocks.LAVA_CAULDRON) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY) || state.is(Blocks.POINTED_DRIPSTONE);
    }

    /**
     * Landing height for one column, or {@link SafeLanding#NONE} when its chunk is not loaded or
     * there is no safe spot.
     *
     * @param hintY feet height to try first in dimensions with a ceiling
     */
    static int landingY(ServerLevel level, int x, int z, int hintY) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return SafeLanding.NONE;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        SafeLanding.Column column = y -> cell(chunk.getBlockState(pos.set(x, y, z)), level, pos);
        int minY = level.getMinY();
        if (level.dimensionType().hasCeiling()) {
            int maxY = Math.min(level.getMaxY(), minY + level.dimensionType().logicalHeight() - 1);
            return SafeLanding.nearHint(column, hintY, minY, maxY);
        }
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        return SafeLanding.surface(column, top, minY, level.getMaxY());
    }
}
