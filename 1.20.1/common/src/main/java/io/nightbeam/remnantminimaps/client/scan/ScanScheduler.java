package io.nightbeam.remnantminimaps.client.scan;

import io.nightbeam.remnantminimaps.client.session.DimensionMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;

/**
 * Decides which loaded chunks to (re)scan each tick: newly received chunks first, then the
 * player's surroundings (so building and digging show up quickly), then a slow sweep over the
 * whole render distance. All under a per-tick time budget.
 */
public final class ScanScheduler {
    private static final int NEAR_REFRESH_TICKS = 10;
    private static final int NEAR_RADIUS = 1;
    private static final int SWEEP_PER_TICK = 3;

    private final ChunkScanner scanner = new ChunkScanner();
    private final ChunkScanner.Result result = new ChunkScanner.Result();
    private final LongArrayFIFOQueue urgent = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private int tickCounter;
    private int sweepRing;
    private int sweepIndex;
    private boolean needsFill = true;

    public void reset() {
        urgent.clear();
        queue.clear();
        queued.clear();
        scanner.clearCache();
        sweepRing = 0;
        sweepIndex = 0;
        needsFill = true;
    }

    /** Queues every chunk in view distance, nearest first, so a fresh map fills in right away. */
    private void fill(int pcx, int pcz, int radius) {
        for (int ring = 0; ring <= radius; ring++) {
            if (ring == 0) {
                enqueue(ChunkPos.asLong(pcx, pcz), false);
                continue;
            }
            for (int i = -ring; i < ring; i++) {
                enqueue(ChunkPos.asLong(pcx + i, pcz - ring), false);
                enqueue(ChunkPos.asLong(pcx + ring, pcz + i), false);
                enqueue(ChunkPos.asLong(pcx - i, pcz + ring), false);
                enqueue(ChunkPos.asLong(pcx - ring, pcz - i), false);
            }
        }
    }

    public void onChunkLoaded(LevelChunk chunk) {
        enqueue(ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z), false);
    }

    private void enqueue(long pos, boolean priority) {
        if (queued.add(pos)) {
            if (priority) {
                urgent.enqueue(pos);
            } else {
                queue.enqueue(pos);
            }
        }
    }

    public void tick(ClientLevel level, DimensionMap map, int playerChunkX, int playerChunkZ, int sliceY,
                     int viewDistance, long budgetNanos) {
        tickCounter++;
        if (needsFill) {
            needsFill = false;
            fill(playerChunkX, playerChunkZ, Math.max(2, viewDistance));
        }
        if (tickCounter % NEAR_REFRESH_TICKS == 0) {
            for (int dz = -NEAR_RADIUS; dz <= NEAR_RADIUS; dz++) {
                for (int dx = -NEAR_RADIUS; dx <= NEAR_RADIUS; dx++) {
                    enqueue(ChunkPos.asLong(playerChunkX + dx, playerChunkZ + dz), true);
                }
            }
        }
        sweep(playerChunkX, playerChunkZ, viewDistance);

        long deadline = System.nanoTime() + budgetNanos;
        while (System.nanoTime() < deadline) {
            long pos;
            if (!urgent.isEmpty()) {
                pos = urgent.dequeueLong();
            } else if (!queue.isEmpty()) {
                pos = queue.dequeueLong();
            } else {
                break;
            }
            queued.remove(pos);
            int cx = ChunkPos.getX(pos);
            int cz = ChunkPos.getZ(pos);
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            scanner.scan(level, chunk, result, sliceY);
            map.applyScan(result);
        }
    }

    /** Walks square rings outward from the player, a few chunks per tick, then starts over. */
    private void sweep(int pcx, int pcz, int viewDistance) {
        int radius = Math.max(2, viewDistance);
        for (int n = 0; n < SWEEP_PER_TICK; n++) {
            int ring = sweepRing;
            int perimeter = ring == 0 ? 1 : ring * 8;
            if (sweepIndex >= perimeter) {
                sweepIndex = 0;
                sweepRing = ring >= radius ? 0 : ring + 1;
                continue;
            }
            int dx;
            int dz;
            if (ring == 0) {
                dx = 0;
                dz = 0;
            } else {
                int side = sweepIndex / (ring * 2);
                int offset = sweepIndex % (ring * 2);
                switch (side) {
                    case 0 -> { dx = -ring + offset; dz = -ring; }
                    case 1 -> { dx = ring; dz = -ring + offset; }
                    case 2 -> { dx = ring - offset; dz = ring; }
                    default -> { dx = -ring; dz = ring - offset; }
                }
            }
            sweepIndex++;
            enqueue(ChunkPos.asLong(pcx + dx, pcz + dz), false);
        }
    }
}
