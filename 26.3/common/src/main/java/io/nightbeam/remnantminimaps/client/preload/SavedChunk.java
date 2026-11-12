package io.nightbeam.remnantminimaps.client.preload;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * A chunk read from the world save: block states and biomes only, detached from the running world
 * (no entities, no block entities, no lighting, nothing is loaded into the server). It answers the
 * scanner's questions the way a loaded chunk would, with biome tints taken from its own biomes.
 * Created and used on the pre-load worker thread.
 */
public final class SavedChunk implements BlockAndTintGetter {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public final int chunkX;
    public final int chunkZ;
    private final int minY;
    private final int height;
    private final int minSectionY;
    private final LevelChunkSection[] sections;
    private final int topY;

    private SavedChunk(int chunkX, int chunkZ, int minY, int height, int minSectionY, LevelChunkSection[] sections) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.height = height;
        this.minSectionY = minSectionY;
        this.sections = sections;
        int top = minY - 1;
        for (int i = sections.length - 1; i >= 0; i--) {
            if (sections[i] != null && !sections[i].hasOnlyAir()) {
                top = ((minSectionY + i) << 4) + 15;
                break;
            }
        }
        this.topY = top;
    }

    /**
     * Turns a chunk's saved data into a {@code SavedChunk}. Data from older game versions is upgraded
     * the same way the game upgrades it when loading.
     *
     * @return null when the chunk is not fully generated yet
     * @throws IllegalStateException when it was saved by a newer game version or cannot be read
     */
    public static SavedChunk read(ServerLevel level, CompoundTag tag) {
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        int version = NbtUtils.getDataVersion(tag, -1);
        if (version > current) {
            throw new IllegalStateException("chunk saved by a newer game version (" + version + ")");
        }
        if (version < current) {
            ChunkMap chunkMap = level.getChunkSource().chunkMap;
            CompoundTag context = ChunkMap.getChunkDataFixContextTag(level.dimension(),
                    level.getChunkSource().getGenerator().getTypeNameForDataFixer());
            tag = chunkMap.upgradeChunkTag(tag, -1, context, current);
        }
        SerializableChunkData data = SerializableChunkData.parse(level, level.palettedContainerFactory(), tag);
        if (!data.chunkStatus().isOrAfter(ChunkStatus.FULL)) {
            return null;
        }
        LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];
        for (SerializableChunkData.SectionData section : data.sectionData()) {
            int index = level.getSectionIndexFromSectionY(section.y());
            if (index >= 0 && index < sections.length && section.chunkSection() != null) {
                sections[index] = section.chunkSection();
            }
        }
        return new SavedChunk(data.chunkPos().x(), data.chunkPos().z(), level.getMinY(), level.getHeight(),
                level.getMinSectionY(), sections);
    }

    /** Highest block that may not be air, or below the world when the chunk is empty. */
    public int topY() {
        return topY;
    }

    private LevelChunkSection section(int y) {
        int index = (y >> 4) - minSectionY;
        return index >= 0 && index < sections.length ? sections[index] : null;
    }

    private boolean inChunk(BlockPos pos) {
        return (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (!inChunk(pos)) {
            return AIR;
        }
        LevelChunkSection section = section(pos.getY());
        return section == null ? AIR : section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    /** Biome at a block, or null outside the chunk's saved sections. */
    public Holder<Biome> biome(BlockPos pos) {
        LevelChunkSection section = section(Math.max(minY, Math.min(minY + height - 1, pos.getY())));
        if (section == null) {
            return null;
        }
        return section.getNoiseBiome((pos.getX() & 15) >> 2, (pos.getY() & 15) >> 2, (pos.getZ() & 15) >> 2);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        Holder<Biome> biome = biome(pos);
        return biome == null ? -1 : resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinY() {
        return minY;
    }
}
