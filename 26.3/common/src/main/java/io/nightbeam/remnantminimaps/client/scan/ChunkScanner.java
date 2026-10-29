package io.nightbeam.remnantminimaps.client.scan;

import io.nightbeam.remnantminimaps.core.map.Columns;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reads the visible top of every column in a loaded chunk. */
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
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        out.chunkX = chunk.getPos().x();
        out.chunkZ = chunk.getPos().z();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lz * 16 + lx;
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int y;
                BlockState state;

                if (sliceY == SURFACE) {
                    y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
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
                            out.colors[i] = classifier.classify(state, level, pos);
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
                out.colors[i] = classifier.classify(state, level, pos);
                out.heights[i] = (short) top;
                out.depths[i] = (byte) depth;
            }
        }
    }
}
