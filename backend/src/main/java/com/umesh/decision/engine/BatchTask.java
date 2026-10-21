package com.umesh.decision.engine;

import java.util.SplittableRandom;
import java.util.concurrent.Callable;

/**
 * One unit of work submitted to the bounded executor: runs {@code iterationsInBatch} independent
 * trajectory simulations and writes each result directly into the shared {@link PathAccumulator}.
 *
 * <p>Every piece of per-task state (the {@link SplittableRandom}, the {@link SystematicRiskSampler})
 * is constructed fresh inside {@link #call()} and never shared with any other task/thread — the
 * only object touched by more than one thread at a time is {@code accumulator}, which is built
 * specifically to be safe for that (see its class comment).
 */
public final class BatchTask implements Callable<BatchTask.Summary> {
    private final PathSimulationSpec spec;
    private final int batchIndex;
    private final int iterationsInBatch;
    private final int batchSize;
    private final PathAccumulator accumulator;

    public BatchTask(PathSimulationSpec spec, int batchIndex, int iterationsInBatch, int batchSize, PathAccumulator accumulator) {
        this.spec = spec;
        this.batchIndex = batchIndex;
        this.iterationsInBatch = iterationsInBatch;
        this.batchSize = batchSize;
        this.accumulator = accumulator;
    }

    @Override
    public Summary call() {
        String pathName = spec.path().name();
        long seed = SeedFactory.batchSeed(spec.runFingerprint(), pathName, batchIndex);
        SplittableRandom random = new SplittableRandom(seed);
        // The shared PathAccumulator's reservoir sampler has a GLOBAL arrival counter spanning
        // every batch running concurrently, so whether a given offer lands in "fill" or
        // "replace" phase (and therefore whether it consumes an extra random draw at all) depends
        // on other threads' scheduling. If that draw came from the same RNG that drives this
        // batch's trajectory math, this batch's own outcome would become scheduling-dependent:
        // the same batch index could compute a different trajectory depending on unrelated
        // threads' timing, breaking the "batch outcome is a pure function of batch index"
        // determinism this engine is built on. Splitting off a dedicated reservoir RNG once,
        // deterministically, before any iterations run keeps the two concerns fully isolated.
        SplittableRandom reservoirRandom = random.split();
        double[] rotation = SeedFactory.rotation(spec.runFingerprint(), pathName, 2);
        long globalStartIndex = (long) batchIndex * batchSize;
        SystematicRiskSampler systematicSampler = new SystematicRiskSampler(rotation, globalStartIndex);

        int goalHitsInBatch = 0;
        for (int i = 0; i < iterationsInBatch; i++) {
            SystematicRiskSampler.Factors factors = systematicSampler.next();
            TrajectoryMath.IterationResult result = TrajectoryMath.simulate(
                    spec.path(), spec.years(), spec.calibration(), spec.targetSalaryLpa(), random, factors);
            accumulator.recordIteration(result.finalSalary(), result.accumulatedWealth(), result.goalHit(),
                    result.downsideEvents(), result.recoveryHits(), result.yearSalaries(), reservoirRandom);
            if (result.goalHit()) {
                goalHitsInBatch++;
            }
        }
        return new Summary(batchIndex, iterationsInBatch, goalHitsInBatch);
    }

    public record Summary(int batchIndex, int iterationsRun, int goalHits) {
    }
}
