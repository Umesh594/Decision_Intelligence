package com.umesh.decision.engine;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe bounded reservoir sample (Algorithm R / Vitter 1985) used to estimate percentiles
 * (p10/p50/p90) over a Monte Carlo run with up to 100,000 iterations per path without retaining
 * every raw value in memory (and without an unbounded checkpoint payload).
 *
 * <p><b>Why a reservoir instead of exact percentiles:</b> exact order statistics require keeping
 * every sample. For a single run that is cheap, but the whole point of this engine is that a run
 * can be checkpointed and resumed, which means the "already seen" values must be durably
 * persisted too. A reservoir caps that payload at a configurable size (default 8,000 doubles)
 * regardless of how many iterations have actually run, trading exact percentiles for a
 * well-understood, uniformly-random approximation whose error shrinks as the reservoir fills. The
 * mean/variance-derived statistics (goal probability, volatility, downside risk, resilience) are
 * computed from exact running sums elsewhere ({@link PathAccumulator}) and are therefore never
 * approximated — only the percentile bands are.
 *
 * <p><b>Concurrency design:</b> {@link #offer} is called concurrently by every batch-worker thread
 * for every iteration it produces. The "should this value replace a slot" decision for an
 * already-full reservoir only needs a lock for the (rare, O(capacity / n)) actual array write;
 * the random draw that decides whether to write at all is done with the caller's own
 * {@link SplittableRandom} (never a shared one) before taking the lock, so the lock is only ever
 * held for a trivial array-slot assignment. A single intrinsic lock is sufficient here: reservoir
 * writes are O(1) and become increasingly rare as more iterations are seen, so lock contention
 * does not compete with the dominant cost in this engine, which is the per-iteration trajectory
 * simulation itself.
 */
public final class ConcurrentReservoirSampler {
    private final double[] reservoir;
    private final AtomicLong seen;
    private final Object lock = new Object();

    public ConcurrentReservoirSampler(int capacity) {
        this(capacity, new double[0], 0L);
    }

    /** Reconstructs a sampler resumed from a checkpoint: {@code alreadySeen} must be the exact total offer count so far. */
    public ConcurrentReservoirSampler(int capacity, double[] seedValues, long alreadySeen) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Reservoir capacity must be positive");
        }
        this.reservoir = new double[capacity];
        int copyLength = Math.min(capacity, seedValues.length);
        System.arraycopy(seedValues, 0, reservoir, 0, copyLength);
        this.seen = new AtomicLong(alreadySeen);
    }

    public void offer(double value, SplittableRandom random) {
        long index = seen.getAndIncrement();
        if (index < reservoir.length) {
            synchronized (lock) {
                reservoir[(int) index] = value;
            }
            return;
        }
        long candidateSlot = random.nextLong(index + 1);
        if (candidateSlot < reservoir.length) {
            synchronized (lock) {
                reservoir[(int) candidateSlot] = value;
            }
        }
    }

    public long seenCount() {
        return seen.get();
    }

    public double[] snapshot() {
        int filled = (int) Math.min(seen.get(), reservoir.length);
        synchronized (lock) {
            return Arrays.copyOf(reservoir, filled);
        }
    }
}
