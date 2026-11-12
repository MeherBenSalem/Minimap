package io.nightbeam.remnantminimaps.client.preload;

import io.nightbeam.remnantminimaps.RemnantMinimaps;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.scan.ChunkScanner;
import io.nightbeam.remnantminimaps.client.session.DimensionMap;
import io.nightbeam.remnantminimaps.core.config.RemnantConfig;
import io.nightbeam.remnantminimaps.core.preload.PreloadJob;
import io.nightbeam.remnantminimaps.core.preload.PreloadPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fills in the map around the player from the world save, without walking there. Single-player (or
 * the host of a LAN world) only: the client reads chunks that already exist in the save of its own
 * built-in server. Nothing is generated and nothing is loaded into the world: each chunk's saved data
 * is read through the server's region storage, decoded on one low-priority worker thread and drawn
 * on the map with the regular scanner.
 *
 * <p>Limits: at most {@link RemnantConfig#preloadChunksPerTick} reads start per tick and at most four
 * times that are in flight; results are drawn within the mapping budget of each tick; new reads wait
 * while the map is still styling earlier ones; the radius is capped by
 * {@link RemnantConfig#preloadMaxRadius}. Client thread only, except for the worker.
 */
public final class MapPreloader {
    /** Regions waiting for restyling before new reads are paused. */
    private static final int STYLE_BACKLOG_LIMIT = 6;
    /** Decoded chunks waiting to be drawn before new reads are paused. */
    private static final int RESULT_QUEUE_LIMIT = 256;
    private static final int PROGRESS_EVERY_TICKS = 10;

    private record Done(PreloadJob.Outcome outcome, ChunkScanner.Result result) {}

    private static final class Run {
        final PreloadJob job;
        final IntegratedServer server;
        final ServerLevel level;
        final String dimension;
        final DimensionMap map;
        final int sliceY;
        final int radiusBlocks;
        final ExecutorService worker;
        final ChunkScanner scanner = new ChunkScanner();
        final ConcurrentLinkedQueue<Done> done = new ConcurrentLinkedQueue<>();
        final long startedAt = System.currentTimeMillis();
        int ticks;

        Run(PreloadJob job, IntegratedServer server, ServerLevel level, String dimension, DimensionMap map, int sliceY,
            int radiusBlocks) {
            this.job = job;
            this.server = server;
            this.level = level;
            this.dimension = dimension;
            this.map = map;
            this.sliceY = sliceY;
            this.radiusBlocks = radiusBlocks;
            this.worker = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Remnants Minimap Pre-load");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        }
    }

    private static Run run;

    private MapPreloader() {}

    public static boolean running() {
        return run != null;
    }

    /** Progress from 0 to 1, or -1 when nothing is running. */
    public static float progress() {
        return run == null ? -1.0f : run.job.progress();
    }

    // ------------------------------------------------------------------ commands

    /** Starts a pre-load of {@code radiusBlocks} around the player. Returns the message to show. */
    public static Component start(int radiusBlocks) {
        Minecraft mc = Minecraft.getInstance();
        if (run != null) {
            return Component.translatable("remnantminimaps.preload.already_running");
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return Component.translatable("remnantminimaps.preload.singleplayer_only");
        }
        ClientLevel clientLevel = mc.level;
        LocalPlayer player = mc.player;
        DimensionMap map = RemnantClient.currentMap();
        if (clientLevel == null || player == null || map == null) {
            return Component.translatable("remnantminimaps.preload.no_world");
        }
        ServerLevel level = server.getLevel(clientLevel.dimension());
        if (level == null) {
            return Component.translatable("remnantminimaps.preload.no_world");
        }
        RemnantConfig config = RemnantClient.config();
        int radius = Math.max(16, radiusBlocks);
        boolean capped = radius > config.preloadMaxRadius;
        radius = Math.min(radius, config.preloadMaxRadius);
        int radiusChunks = (radius + 15) >> 4;
        int sliceY = clientLevel.dimensionType().hasCeiling() ? Mth.floor(player.getY()) + 1 : ChunkScanner.SURFACE;
        PreloadPlan plan = new PreloadPlan(player.chunkPosition().x, player.chunkPosition().z, radiusChunks);
        PreloadJob job = new PreloadJob(plan, config.preloadChunksPerTick, config.preloadChunksPerTick * 4);
        run = new Run(job, server, level, map.dimensionId, map, sliceY, radius);
        RemnantMinimaps.LOG.info("Pre-loading the map: {} chunks within {} blocks of {} {} in {}", plan.total(), radius,
                player.getBlockX(), player.getBlockZ(), map.dimensionId);
        Component started = Component.translatable("remnantminimaps.preload.started", plan.total(), radius);
        return capped ? Component.translatable("remnantminimaps.preload.capped", config.preloadMaxRadius).append(" ").append(started)
                : started;
    }

    public static Component cancel() {
        if (run == null) {
            return Component.translatable("remnantminimaps.preload.not_running");
        }
        Run r = run;
        stop();
        return summary("remnantminimaps.preload.cancelled", r);
    }

    public static Component status() {
        if (run == null) {
            return Component.translatable("remnantminimaps.preload.not_running");
        }
        return progressMessage(run);
    }

    /** Ends any running pre-load quietly (leaving the world, closing the map session). */
    public static void stop() {
        if (run != null) {
            run.job.cancel();
            run.worker.shutdownNow();
            run = null;
        }
    }

    // ------------------------------------------------------------------ ticking

    /** Called every client tick after the map has ticked. */
    public static void tick(Minecraft mc) {
        Run r = run;
        if (r == null) {
            return;
        }
        String reason = invalidReason(mc, r);
        if (reason != null) {
            stop();
            message(mc, summary(reason, r));
            return;
        }
        RemnantConfig config = RemnantClient.config();
        long budget = (long) (config.scanBudgetMs * 1_000_000L);
        PreloadJob.drain(r.done, done -> {
            r.job.complete(done.outcome());
            if (done.result() != null) {
                r.map.applyScan(done.result());
            }
        }, budget, System::nanoTime);

        boolean paused = r.map.styleBacklog() > STYLE_BACKLOG_LIMIT || r.done.size() > RESULT_QUEUE_LIMIT;
        ClientLevel clientLevel = mc.level;
        r.job.issue(pos -> isInView(clientLevel, pos), pos -> read(r, pos), paused);

        r.ticks++;
        if (r.job.finished()) {
            stop();
            message(mc, summary("remnantminimaps.preload.finished", r));
            RemnantMinimaps.LOG.info("Map pre-load finished in {} s: {} drawn, {} never generated, {} unfinished, {} in view, {} unreadable",
                    (System.currentTimeMillis() - r.startedAt) / 1000, r.job.count(PreloadJob.Outcome.DRAWN),
                    r.job.count(PreloadJob.Outcome.NOT_GENERATED), r.job.count(PreloadJob.Outcome.NOT_FINISHED),
                    r.job.count(PreloadJob.Outcome.IN_VIEW), r.job.count(PreloadJob.Outcome.UNREADABLE));
        } else if (r.ticks % PROGRESS_EVERY_TICKS == 0 && mc.player != null) {
            mc.player.displayClientMessage(progressMessage(r), true);
        }
    }

    private static String invalidReason(Minecraft mc, Run r) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() != r.server || !r.server.isRunning()) {
            return "remnantminimaps.preload.stopped_left";
        }
        if (RemnantClient.currentMap() != r.map || !r.dimension.equals(RemnantClient.currentDimension())) {
            return "remnantminimaps.preload.stopped_dimension";
        }
        return null;
    }

    /** Chunks the client has loaded are mapped live; the save may even be older than what the player sees. */
    private static boolean isInView(ClientLevel level, long pos) {
        return level != null && level.getChunkSource().getChunk(PreloadPlan.unpackX(pos), PreloadPlan.unpackZ(pos),
                ChunkStatus.FULL, false) != null;
    }

    private static void read(Run r, long pos) {
        ChunkPos chunk = new ChunkPos(PreloadPlan.unpackX(pos), PreloadPlan.unpackZ(pos));
        try {
            r.level.getChunkSource().chunkMap.read(chunk)
                    .thenApplyAsync(tag -> decode(r, chunk, tag), r.worker)
                    .exceptionally(error -> new Done(PreloadJob.Outcome.UNREADABLE, null))
                    .thenAccept(r.done::add);
        } catch (RuntimeException e) {
            r.done.add(new Done(PreloadJob.Outcome.UNREADABLE, null));
        }
    }

    /** Worker thread: turns saved data into a map scan. */
    private static Done decode(Run r, ChunkPos pos, Optional<CompoundTag> tag) {
        if (tag.isEmpty()) {
            return new Done(PreloadJob.Outcome.NOT_GENERATED, null);
        }
        try {
            SavedChunk chunk = SavedChunk.read(r.level, pos.x, pos.z, tag.get());
            if (chunk == null) {
                return new Done(PreloadJob.Outcome.NOT_FINISHED, null);
            }
            ChunkScanner.Result result = new ChunkScanner.Result();
            r.scanner.scan(chunk, result, r.sliceY);
            return new Done(PreloadJob.Outcome.DRAWN, result);
        } catch (RuntimeException e) {
            RemnantMinimaps.LOG.debug("Pre-load could not read a chunk", e);
            return new Done(PreloadJob.Outcome.UNREADABLE, null);
        }
    }

    // ------------------------------------------------------------------ messages

    private static Component progressMessage(Run r) {
        return Component.translatable("remnantminimaps.preload.progress", r.job.done(), r.job.total(),
                Math.round(r.job.progress() * 100));
    }

    private static Component summary(String key, Run r) {
        PreloadJob job = r.job;
        return Component.translatable(key, job.count(PreloadJob.Outcome.DRAWN), job.count(PreloadJob.Outcome.NOT_GENERATED)
                + job.count(PreloadJob.Outcome.NOT_FINISHED), job.count(PreloadJob.Outcome.IN_VIEW),
                job.count(PreloadJob.Outcome.UNREADABLE));
    }

    private static void message(Minecraft mc, Component text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(text, false);
        }
    }
}
