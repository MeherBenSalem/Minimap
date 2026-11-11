package io.nightbeam.remnantminimaps.core.preload;

import io.nightbeam.remnantminimaps.core.preload.PreloadJob.Outcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreloadJobTest {
    @Test
    void neverStartsMoreThanPerTickNorKeepsMoreThanMaxInFlight() {
        PreloadJob job = new PreloadJob(new PreloadPlan(0, 0, 10), 8, 20);
        List<Long> requested = new ArrayList<>();
        assertEquals(8, job.issue(pos -> false, requested::add, false));
        assertEquals(8, job.issue(pos -> false, requested::add, false));
        assertEquals(4, job.issue(pos -> false, requested::add, false), "capped by in-flight");
        assertEquals(0, job.issue(pos -> false, requested::add, false));
        assertEquals(20, job.inFlight());
        job.complete(Outcome.DRAWN);
        assertEquals(1, job.issue(pos -> false, requested::add, false));
        assertEquals(21, requested.size());
    }

    @Test
    void backPressurePausesRequests() {
        PreloadJob job = new PreloadJob(new PreloadPlan(0, 0, 4), 8, 32);
        assertEquals(0, job.issue(pos -> false, pos -> {}, true));
        assertEquals(0, job.inFlight());
        assertEquals(8, job.issue(pos -> false, pos -> {}, false));
    }

    @Test
    void chunksInViewAreCountedWithoutARequest() {
        PreloadJob job = new PreloadJob(new PreloadPlan(0, 0, 1), 8, 32);
        List<Long> requested = new ArrayList<>();
        job.issue(pos -> PreloadPlan.unpackX(pos) == 0 && PreloadPlan.unpackZ(pos) == 0, requested::add, false);
        assertEquals(1, job.count(Outcome.IN_VIEW));
        assertEquals(4, requested.size());
        assertFalse(requested.contains(PreloadPlan.pack(0, 0)));
    }

    @Test
    void finishesWhenEveryChunkHasAnOutcomeAndCountsAddUp() {
        PreloadJob job = new PreloadJob(new PreloadPlan(0, 0, 6), 4, 8);
        Queue<Long> open = new ArrayDeque<>();
        Outcome[] cycle = {Outcome.DRAWN, Outcome.NOT_GENERATED, Outcome.NOT_FINISHED, Outcome.UNREADABLE};
        int i = 0;
        int ticks = 0;
        while (!job.finished()) {
            job.issue(pos -> false, open::add, false);
            while (!open.isEmpty()) {
                open.poll();
                job.complete(cycle[i++ % cycle.length]);
            }
            assertTrue(++ticks < 10_000);
        }
        assertEquals(job.total(), job.done());
        assertEquals(1.0f, job.progress());
        int sum = 0;
        for (Outcome o : Outcome.values()) {
            sum += job.count(o);
        }
        assertEquals(job.total(), sum);
    }

    @Test
    void cancelStopsRequestsButWaitsForThoseInFlight() {
        PreloadJob job = new PreloadJob(new PreloadPlan(0, 0, 10), 4, 8);
        job.issue(pos -> false, pos -> {}, false);
        job.cancel();
        assertEquals(0, job.issue(pos -> false, pos -> {}, false));
        assertFalse(job.finished(), "four requests are still open");
        for (int n = 0; n < 4; n++) {
            job.complete(Outcome.DRAWN);
        }
        assertTrue(job.finished());
        assertTrue(job.cancelled());
    }

    @Test
    void completeWithoutRequestIsAnError() {
        PreloadJob job = new PreloadJob(new PreloadPlan(0, 0, 1), 1, 1);
        assertThrows(IllegalStateException.class, () -> job.complete(Outcome.DRAWN));
        assertThrows(IllegalArgumentException.class, () -> new PreloadJob(new PreloadPlan(0, 0, 1), 0, 1));
    }

    @Test
    void drainStopsAtTheBudgetButAlwaysMakesProgress() {
        Queue<Integer> queue = new ArrayDeque<>(List.of(1, 2, 3, 4, 5, 6));
        long[] now = {0};
        List<Integer> applied = new ArrayList<>();
        // each item "takes" 1 ms on a fake clock; the budget is 2.5 ms
        int n = PreloadJob.drain(queue, item -> {
            applied.add(item);
            now[0] += 1_000_000;
        }, 2_500_000, () -> now[0]);
        assertEquals(3, n);
        assertEquals(List.of(1, 2, 3), applied);
        // with no budget left at all, one item still goes through
        assertEquals(1, PreloadJob.drain(queue, item -> now[0] += 10_000_000, 0, () -> now[0]));
        assertEquals(2, queue.size());
        assertEquals(0, PreloadJob.drain(new ArrayDeque<Integer>(), item -> {}, 1_000_000, () -> now[0]));
    }
}
