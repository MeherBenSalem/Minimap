package io.nightbeam.remnantminimaps.client.scan;

import io.nightbeam.remnantminimaps.client.preload.SavedChunk;
import io.nightbeam.remnantminimaps.core.map.Columns;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reads the visible top of every column in a loaded chunk, or in a chunk read from the save. */
public final class ChunkScanner {
    /** Sentinel for {@link #scan}: map the open-sky surface. */
    public static final int SURFACE = Integer.MIN_VALUE;

    private static final int VOID_RGB = 0x14121A;
    private static final int MAX_WALL_SEARCH = 48;

    public static final class Result {
        public final int[] colors = new int[256];
        public final short[] heights = new short[256];
        public final byte[] depths = new byte[256];
        public int chunkX;
        public int chunkZ;
    }

    /** Where to start looking down from in a column (local coordinates). */
    @FunctionalInterface
    private interface Surface {
        int at(int lx, int lz);
    }

    /** Packed column color of a surface block. */
    @FunctionalInterface
    private interface Colorizer {
        int classify(BlockState state, BlockPos pos);
    }

    private final BlockClassifier classifier = new BlockClassifier();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public void clearCache() {
        classifier.clear();
    }

    /**
     * @param sliceY {@link #SURFACE} for open-sky dimensions, otherwise the Y level to map below
     *               (used for dimensions with a ceiling, such as the Nether).
     */
    public void scan(ClientLevel level, LevelChunk chunk, Result out, int sliceY) {
        scan(chunk, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1, chunk.getPos().x, chunk.getPos().z,
                (lx, lz) -> chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz),
                (state, p) -> classifier.classify(state, level, p), out, sliceY);
    }

    /**
     * Same for a chunk read from the world save (pre-loading). Biome tints come from the chunk's own
     * biomes, so nothing of the running world is touched; safe on a worker thread when this scanner
     * is used by that thread only.
     */
    public void scan(SavedChunk chunk, Result out, int sliceY) {
        scan(chunk, chunk.getMinBuildHeight(), chunk.getMaxY(), chunk.chunkX, chunk.chunkZ, (lx, lz) -> chunk.topY(),
                (state, p) -> classifier.classify(state, chunk, chunk.biome(p), p), out, sliceY);
    }

    private void scan(BlockGetter chunk, int minY, int maxY, int chunkX, int chunkZ, Surface surface, Colorizer colorizer,
                      Result out, int sliceY) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        out.chunkX = chunkX;
        out.chunkZ = chunkZ;

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lz * 16 + lx;
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int y;
                BlockState state;

                if (sliceY == SURFACE) {
                    y = surface.at(lx, lz);
                } else {
                    y = Math.max(minY, Math.min(maxY - 1, sliceY));
                    pos.set(wx, y, wz);
                    state = chunk.getBlockState(pos);
                    if (!BlockClassifier.isSeeThrough(state, chunk, pos) && !BlockClassifier.isWater(state) && !BlockClassifier.isLava(state)) {
                        // Started inside rock: look for the cave space below, else draw it as a wall.
                        int start = y;
                        while (y > minY && start - y < MAX_WALL_SEARCH) {
                            pos.setY(--y);
                            if (BlockClassifier.isSeeThrough(chunk.getBlockState(pos), chunk, pos)) {
                                break;
                            }
                        }
                        if (start - y >= MAX_WALL_SEARCH || y <= minY) {
                            pos.setY(start);
                            out.colors[i] = colorizer.classify(state, pos);
                            out.heights[i] = (short) start;
                            out.depths[i] = 0;
                            continue;
                        }
                    }
                }

                state = null;
                while (y >= minY) {
                    pos.set(wx, y, wz);
                    state = chunk.getBlockState(pos);
                    if (!BlockClassifier.isSeeThrough(state, chunk, pos)) {
                        break;
                    }
                    y--;
                }
                if (y < minY || state == null) {
                    out.colors[i] = Columns.pack(Columns.VOID, VOID_RGB);
                    out.heights[i] = (short) minY;
                    out.depths[i] = 0;
                    continue;
                }

                int top = y;
                int depth = 0;
                if (BlockClassifier.isWater(state)) {
                    depth = 1;
                    int below = y - 1;
                    while (below >= minY && depth < 255) {
                        pos.setY(below);
                        if (!BlockClassifier.isWater(chunk.getBlockState(pos))) {
                            break;
                        }
                        depth++;
                        below--;
                    }
                    pos.setY(top);
                }
                out.colors[i] = colorizer.classify(state, pos);
                out.heights[i] = (short) top;
                out.depths[i] = (byte) depth;
            }
        }
    }
}
