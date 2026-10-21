package com.umesh.decision.engine;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free, numerically-stable online mean/variance accumulator used to decide when the Monte
 * Carlo engine has converged.
 *
 * <p>Batches (not individual iterations) feed their summary {@code (n, mean, M2)} into
 * {@link #combine(long, double, double)} via Chan, Golub &amp; LeVeque's parallel variance
 * combination formula, applied optimistically with a compare-and-swap retry loop against an
 * immutable {@link State} snapshot. This is preferred over a plain {@code sum}/{@code sumOfSquares}
 * pair (which would also be thread-safe via {@code DoubleAdder}) because Welford-style combination
 * avoids the catastrophic cancellation that {@code E[X^2] - E[X]^2} is prone to when the running
 * mean is not small relative to the variance — a real concern here since the convergence signal is
 * queried repeatedly, live, while the run is in flight. It is deliberately applied at batch
 * granularity rather than per-iteration: per-iteration CAS from every worker thread would create
 * heavy cache-line contention for no benefit, since the convergence check only needs to run
 * between waves anyway. The exact per-iteration sums used for the final reported statistics live
 * in {@link PathAccumulator} via {@code DoubleAdder}/{@code LongAdder}, which is the right
 * structure for that write-heavy, low-contention-sensitivity job; this class is reserved for the
 * smaller number of batch-level updates where numerical stability of the live estimate matters
 * more than raw write throughput.
 */
public final class ConcurrentRunningStats {
    private final AtomicReference<State> state = new AtomicReference<>(new State(0L, 0.0, 0.0));

    private record State(long count, double mean, double m2) {
    }

    /**
     * Merge a batch summarized by {@code n} observations with mean {@code batchMean} and
     * within-batch sum-of-squared-deviations {@code batchM2} into the running totals.
     */
    public void combine(long n, double batchMean, double batchM2) {
        if (n <= 0) {
            return;
        }
        State previous;
        State next;
        do {
            previous = state.get();
            long combinedCount = previous.count() + n;
            double delta = batchMean - previous.mean();
            double combinedMean = previous.mean() + delta * n / combinedCount;
            double combinedM2 = previous.m2() + batchM2
                    + delta * delta * previous.count() * n / (double) combinedCount;
            next = new State(combinedCount, combinedMean, combinedM2);
        } while (!state.compareAndSet(previous, next));
    }

    public Snapshot snapshot() {
        State s = state.get();
        double variance = s.count() > 0 ? s.m2() / s.count() : 0.0;
        return new Snapshot(s.count(), s.mean(), Math.max(0.0, variance));
    }

    public record Snapshot(long count, double mean, double variance) {
        public double standardError() {
            return count == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(variance / count);
        }

        /** 95% confidence-interval half-width, in the same units as {@code mean}. */
        public double ci95HalfWidth() {
            return 1.959963985 * standardError();
        }
    }
}
