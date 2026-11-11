package io.nightbeam.remnantminimaps.client.preload;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;

/**
 * A chunk read from the world save: block states and biomes only, detached from the running world
 * (no entities, no block entities, no lighting, nothing is loaded into the server). It answers the
 * scanner's questions the way a loaded chunk would, with biome tints taken from its own biomes.
 * Created and used on the pre-load worker thread.
 */
public final class SavedChunk implements BlockAndTintGetter {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** Same codec the game uses for a section's blocks. */
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, AIR);
    /** Saved chunks carry no live lighting; nothing on the scanner's path asks for it. */
    private static final LevelLightEngine NO_LIGHT = new LevelLightEngine(new LightChunkGetter() {
        @Override
        public LightChunk getChunkForLighting(int x, int z) {
            return null;
        }

        @Override
        public BlockGetter getLevel() {
            return EmptyBlockGetter.INSTANCE;
        }
    }, false, false);
    /** Chunks saved before 1.13 need the overworld's structure data to upgrade; those are skipped. */
    private static final int OLDEST_SUPPORTED_DATA_VERSION = 1493;

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
     * @throws IllegalStateException when it was saved by a newer or a very old game version, or cannot be read
     */
    public static SavedChunk read(ServerLevel level, int chunkX, int chunkZ, CompoundTag tag) {
        int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        int version = NbtUtils.getDataVersion(tag, -1);
        if (version > current) {
            throw new IllegalStateException("chunk saved by a newer game version (" + version + ")");
        }
        if (version < current) {
            if (version < OLDEST_SUPPORTED_DATA_VERSION) {
                throw new IllegalStateException("chunk saved before 1.13 (" + version + ")");
            }
            tag = level.getChunkSource().chunkMap.upgradeChunkTag(level.dimension(), () -> {
                throw new IllegalStateException("structure data is only needed before 1.13");
            }, tag, level.getChunkSource().getGenerator().getTypeNameForDataFixer());
        }
        if (ChunkSerializer.getChunkTypeFromTag(tag) != ChunkStatus.ChunkType.LEVELCHUNK) {
            return null;
        }
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> plains = biomes.getHolderOrThrow(Biomes.PLAINS);
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = PalettedContainer.codecRO(
                biomes.asHolderIdMap(), biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, plains);

        LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];
        ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag section = list.getCompound(i);
            int index = level.getSectionIndexFromSectionY(section.getByte("Y"));
            if (index < 0 || index >= sections.length) {
                continue;
            }
            PalettedContainer<BlockState> states = section.contains("block_states", Tag.TAG_COMPOUND)
                    ? decode(BLOCK_STATES, section.getCompound("block_states"))
                    : new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, AIR, PalettedContainer.Strategy.SECTION_STATES);
            PalettedContainerRO<Holder<Biome>> biomeData = section.contains("biomes", Tag.TAG_COMPOUND)
                    ? decode(biomeCodec, section.getCompound("biomes"))
                    : new PalettedContainer<>(biomes.asHolderIdMap(), plains, PalettedContainer.Strategy.SECTION_BIOMES);
            sections[index] = new LevelChunkSection(states, biomeData);
        }
        return new SavedChunk(chunkX, chunkZ, level.getMinBuildHeight(), level.getHeight(), level.getMinSection(), sections);
    }

    private static <T> T decode(Codec<T> codec, CompoundTag tag) {
        Optional<T> value = codec.parse(NbtOps.INSTANCE, tag).result();
        return value.orElseThrow(() -> new IllegalStateException("damaged chunk section"));
    }

    /** Highest block that may not be air, or below the world when the chunk is empty. */
    public int topY() {
        return topY;
    }

    /** Highest block of the world. */
    public int getMaxY() {
        return minY + height - 1;
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
    public float getShade(Direction direction, boolean shade) {
        return 1.0f;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return NO_LIGHT;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinBuildHeight() {
        return minY;
    }
}
