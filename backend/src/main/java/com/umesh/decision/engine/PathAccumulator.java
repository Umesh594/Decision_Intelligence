package com.umesh.decision.engine;

import java.util.SplittableRandom;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Shared mutable aggregation state for one decision path's Monte Carlo simulation, written to
 * concurrently by every batch-worker thread as each iteration of the trajectory completes.
 *
 * <p><b>Concurrency choice:</b> every counter/sum here is a {@link LongAdder} or
 * {@link DoubleAdder} rather than a plain {@code long}/{@code double} behind a lock, or a single
 * {@code AtomicLong}/compare-and-swap loop. Adders are specifically designed for this access
 * pattern — many threads doing frequent, independent, order-insensitive additions with
 * infrequent reads — by internally striping the counter across cells to avoid cache-line
 * contention ("false sharing") between threads updating the same logical counter. A single
 * {@code AtomicLong} would serialize every increment through one contended cache line under N
 * concurrent workers; a lock would be worse. Reads ({@code sum()}) are only needed between waves
 * (for checkpointing) and at the very end (for the final {@code PathOutcome}), which is exactly
 * the read-rarely/write-often profile adders are built for. All of these sums are exact — no
 * approximation — since there is no bound on how many of them we need to keep (unlike the
 * percentile reservoir below, which is deliberately bounded).
 */
public final class PathAccumulator {
    private final int years;
    private final LongAdder completedIterations = new LongAdder();
    private final LongAdder goalHits = new LongAdder();
    private final LongAdder downsideEvents = new LongAdder();
    private final LongAdder recoveryHits = new LongAdder();
    private final DoubleAdder sumFinalSalary = new DoubleAdder();
    private final DoubleAdder sumFinalSalarySq = new DoubleAdder();
    private final DoubleAdder sumWealth = new DoubleAdder();
    private final DoubleAdder[] yearlyTotals;
    private final ConcurrentReservoirSampler finalSalaryReservoir;

    public PathAccumulator(int years, int reservoirCapacity) {
        this(years, reservoirCapacity, new double[0], 0L, 0L, 0L, 0L, 0L, 0.0, 0.0, 0.0, new double[years]);
    }

    /** Reconstructs an accumulator resumed from a persisted checkpoint; all totals are exact carry-forwards. */
    public PathAccumulator(int years, int reservoirCapacity, double[] reservoirSeedValues, long reservoirSeenCount,
                           long completedIterationsSoFar, long goalHitsSoFar, long downsideEventsSoFar,
                           long recoveryHitsSoFar, double sumFinalSalarySoFar, double sumFinalSalarySqSoFar,
                           double sumWealthSoFar, double[] yearlyTotalsSoFar) {
        this.years = years;
        this.finalSalaryReservoir = new ConcurrentReservoirSampler(reservoirCapacity, reservoirSeedValues, reservoirSeenCount);
        this.completedIterations.add(completedIterationsSoFar);
        this.goalHits.add(goalHitsSoFar);
        this.downsideEvents.add(downsideEventsSoFar);
        this.recoveryHits.add(recoveryHitsSoFar);
        this.sumFinalSalary.add(sumFinalSalarySoFar);
        this.sumFinalSalarySq.add(sumFinalSalarySqSoFar);
        this.sumWealth.add(sumWealthSoFar);
        this.yearlyTotals = new DoubleAdder[years];
        for (int year = 0; year < years; year++) {
            this.yearlyTotals[year] = new DoubleAdder();
            if (yearlyTotalsSoFar != null && year < yearlyTotalsSoFar.length) {
                this.yearlyTotals[year].add(yearlyTotalsSoFar[year]);
            }
        }
    }

    /** Called once per completed iteration, concurrently, by whichever worker thread produced it. */
    public void recordIteration(double finalSalary, double wealth, boolean goalHit, int downsideEventsInIteration,
                                int recoveryHitsInIteration, double[] yearSalaries, SplittableRandom random) {
        completedIterations.increment();
        if (goalHit) {
            goalHits.increment();
        }
        if (downsideEventsInIteration > 0) {
            downsideEvents.add(downsideEventsInIteration);
        }
        if (recoveryHitsInIteration > 0) {
            recoveryHits.add(recoveryHitsInIteration);
        }
        sumFinalSalary.add(finalSalary);
        sumFinalSalarySq.add(finalSalary * finalSalary);
        sumWealth.add(wealth);
        for (int year = 0; year < years; year++) {
            yearlyTotals[year].add(yearSalaries[year]);
        }
        finalSalaryReservoir.offer(finalSalary, random);
    }

    public long completedIterations() {
        return completedIterations.sum();
    }

    public long goalHits() {
        return goalHits.sum();
    }

    public long downsideEvents() {
        return downsideEvents.sum();
    }

    public long recoveryHits() {
        return recoveryHits.sum();
    }

    public double sumFinalSalary() {
        return sumFinalSalary.sum();
    }

    public double sumFinalSalarySq() {
        return sumFinalSalarySq.sum();
    }

    public double sumWealth() {
        return sumWealth.sum();
    }

    public double[] yearlyTotals() {
        double[] result = new double[years];
        for (int year = 0; year < years; year++) {
            result[year] = yearlyTotals[year].sum();
        }
        return result;
    }

    public ConcurrentReservoirSampler reservoir() {
        return finalSalaryReservoir;
    }

    public double mean() {
        long n = completedIterations();
        return n == 0 ? 0.0 : sumFinalSalary() / n;
    }

    /** Exact population variance from the running sums; clamped to 0 to guard against floating-point noise. */
    public double variance() {
        long n = completedIterations();
        if (n == 0) {
            return 0.0;
        }
        double mean = mean();
        double variance = sumFinalSalarySq() / n - mean * mean;
        return Math.max(0.0, variance);
    }
}
