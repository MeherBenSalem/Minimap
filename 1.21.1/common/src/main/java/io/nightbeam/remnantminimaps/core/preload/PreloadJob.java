package io.nightbeam.remnantminimaps.core.preload;

import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

/**
 * Bookkeeping of one pre-load: which chunks to ask for next, how many requests are in flight, and
 * how each chunk ended. It never asks for more than {@code perTick} chunks per tick nor keeps more
 * than {@code maxInFlight} requests open, and it stops asking while the caller reports back-pressure.
 * Pure logic, used from the client thread only.
 */
public final class PreloadJob {
    /** How one chunk of the plan ended. */
    public enum Outcome {
        /** Read from the save and drawn on the map. */
        DRAWN,
        /** Not in the save: never generated. Pre-loading never generates terrain. */
        NOT_GENERATED,
        /** In the save but not fully generated yet (the edge of the explored world). */
        NOT_FINISHED,
        /** Loaded around the player already; the live map keeps it up to date. */
        IN_VIEW,
        /** Saved by a newer game, or damaged. */
        UNREADABLE
    }

    private final PreloadPlan plan;
    private final int perTick;
    private final int maxInFlight;
    private final int[] counts = new int[Outcome.values().length];
    private int inFlight;
    private boolean cancelled;

    public PreloadJob(PreloadPlan plan, int perTick, int maxInFlight) {
        if (perTick < 1 || maxInFlight < 1) {
            throw new IllegalArgumentException("perTick and maxInFlight must be positive");
        }
        this.plan = plan;
        this.perTick = perTick;
        this.maxInFlight = maxInFlight;
    }

    /**
     * Hands out the chunks to request this tick.
     *
     * @param inView   chunks the caller already maps live; they are counted as {@link Outcome#IN_VIEW}
     *                 without a request (at most {@code 4 * perTick} of them per tick)
     * @param request  receives each chunk to read; must eventually be answered with {@link #complete}
     * @param paused   back-pressure from the caller (for example the map is still busy drawing)
     * @return the number of requests started
     */
    public int issue(LongPredicate inView, LongConsumer request, boolean paused) {
        if (cancelled || paused) {
            return 0;
        }
        int started = 0;
        int skipped = 0;
        while (started < perTick && inFlight < maxInFlight && plan.hasNext() && skipped < perTick * 4) {
            long pos = plan.next();
            if (inView.test(pos)) {
                counts[Outcome.IN_VIEW.ordinal()]++;
                skipped++;
                continue;
            }
            inFlight++;
            started++;
            request.accept(pos);
        }
        return started;
    }

    /** Records the answer to one request made through {@link #issue}. */
    public void complete(Outcome outcome) {
        if (inFlight <= 0) {
            throw new IllegalStateException("complete() without a request in flight");
        }
        inFlight--;
        counts[outcome.ordinal()]++;
    }

    /** Stops handing out chunks. Requests in flight may still complete. */
    public void cancel() {
        cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }

    /** True once every planned chunk has an outcome, or after a cancel once nothing is in flight. */
    public boolean finished() {
        return inFlight == 0 && (cancelled || !plan.hasNext());
    }

    public int inFlight() {
        return inFlight;
    }

    public int count(Outcome outcome) {
        return counts[outcome.ordinal()];
    }

    /** Chunks with an outcome so far. */
    public int done() {
        int sum = 0;
        for (int c : counts) {
            sum += c;
        }
        return sum;
    }

    public int total() {
        return plan.total();
    }

    /** Progress from 0 to 1. */
    public float progress() {
        return plan.total() == 0 ? 1.0f : done() / (float) plan.total();
    }

    /**
     * Applies queued items until the queue is empty or {@code budgetNanos} have passed. At least one
     * item is applied when the queue is not empty, so work always moves forward.
     *
     * @return the number of items applied
     */
    public static <T> int drain(Queue<T> queue, Consumer<T> apply, long budgetNanos, LongSupplier nanoClock) {
        long deadline = nanoClock.getAsLong() + budgetNanos;
        int applied = 0;
        T item;
        while ((item = queue.poll()) != null) {
            apply.accept(item);
            applied++;
            if (nanoClock.getAsLong() >= deadline) {
                break;
            }
        }
        return applied;
    }
}
