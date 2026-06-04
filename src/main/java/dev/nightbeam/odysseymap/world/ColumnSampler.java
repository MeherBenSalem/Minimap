package dev.nightbeam.odysseymap.world;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import dev.nightbeam.odysseymap.color.BlockColorRegistry;
import dev.nightbeam.odysseymap.color.ColorPalette;
import dev.nightbeam.odysseymap.config.BlockOverrideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ported column sampling from VanillaWorldMinimapRenderer with RGB output.
 */
public class ColumnSampler {
    private final BlockPos.MutableBlockPos blockPos1 = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos blockPos2 = new BlockPos.MutableBlockPos();

    public int sampleColumn(Level level, int worldX, int worldZ) {
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(worldX), SectionPos.blockToSectionCoord(worldZ));
        if (chunk.isEmpty()) {
            return 0x00000000;
        }

        Multiset<MapColor> multiset = LinkedHashMultiset.create();
        AtomicInteger fluidDepth = new AtomicInteger();
        double height = fillColorSet(level, chunk, worldX, worldZ, fluidDepth, multiset);
        return storeMapColor(level, worldX, worldZ, multiset, height, fluidDepth.get());
    }

    private double fillColorSet(Level level, LevelChunk chunk, int worldX, int worldZ,
                                AtomicInteger fluidDepth, Multiset<MapColor> multiset) {
        double d1 = 0.0;
        if (DimensionStyle.hasCeiling(level)) {
            multiset.add(DimensionStyle.ceilingDitherColor(worldX, worldZ, level), 100);
            return 100.0;
        }

        blockPos1.set(worldX, 0, worldZ);
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos1.getX(), blockPos1.getZ()) + 1;
        BlockState blockState;
        if (surfaceY <= level.getMinBuildHeight() + 1) {
            blockState = Blocks.BEDROCK.defaultBlockState();
        } else {
            do {
                --surfaceY;
                blockPos1.setY(surfaceY);
                blockState = chunk.getBlockState(blockPos1);
            } while (blockState.getMapColor(level, blockPos1) == MapColor.NONE && surfaceY > level.getMinBuildHeight());

            if (surfaceY > level.getMinBuildHeight() && !blockState.getFluidState().isEmpty()) {
                int below = surfaceY - 1;
                blockPos2.set(blockPos1);
                BlockState belowState;
                do {
                    blockPos2.setY(below--);
                    belowState = chunk.getBlockState(blockPos2);
                    fluidDepth.incrementAndGet();
                } while (below > level.getMinBuildHeight() && !belowState.getFluidState().isEmpty());
                blockState = correctStateForFluid(level, blockState, blockPos1);
            }
        }

        d1 += surfaceY;
        MapColor color = BlockOverrideConfig.getResolvedOverrides().get(blockState);
        if (color == null) {
            color = blockState.getMapColor(level, blockPos1);
        }
        if (color == null || color == MapColor.NONE) {
            color = MapColor.byId(11);
        }
        multiset.add(color);
        return d1;
    }

    private int storeMapColor(Level level, int worldX, int worldZ, Multiset<MapColor> multiset,
                            double height, int fluidDepth) {
        MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
        if (mapColor == MapColor.NONE) {
            return 0x00000000;
        }

        MapColor.Brightness brightness;
        if (mapColor == MapColor.WATER) {
            brightness = ColorPalette.waterBrightness(fluidDepth, worldX, worldZ);
        } else {
            brightness = ColorPalette.terrainBrightness(0, worldX, worldZ);
        }

        blockPos1.set(worldX, (int) height, worldZ);
        BlockState state = level.getBlockState(blockPos1);
        if (state.isAir()) {
            state = Blocks.STONE.defaultBlockState();
        }
        return BlockColorRegistry.resolveRgb(level, blockPos1, state, mapColor, brightness);
    }

    private BlockState correctStateForFluid(Level world, BlockState state, BlockPos pos) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !state.isFaceSturdy(world, pos, Direction.UP)
                ? fluid.createLegacyBlock() : state;
    }
}
