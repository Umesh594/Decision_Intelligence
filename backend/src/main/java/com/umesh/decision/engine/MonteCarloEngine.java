package com.umesh.decision.engine;

import com.umesh.decision.config.AppProperties;
import com.umesh.decision.config.EngineExecutorConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Concurrent, convergence-driven Monte Carlo engine for one decision path.
 *
 * <p><b>Execution model:</b> the total iteration budget is split into fixed-size batches. Batches
 * are submitted to the shared, bounded {@link ExecutorService} (see {@link EngineExecutorConfig})
 * in waves sized to the worker pool so the engine never has more in-flight work than the pool can
 * execute at once — the executor's own bounded queue plus {@code CallerRunsPolicy} is the
 * backpressure mechanism if a wave is ever larger than queue capacity. After each wave completes,
 * the engine checks the running 95% confidence-interval half-width on the goal-probability
 * estimate (via {@link ConcurrentRunningStats}); once that half-width narrows below the configured
 * threshold <i>and</i> a minimum number of batches has run (so a single lucky/unlucky early batch
 * can't trigger a false-positive stop), the engine stops submitting further waves. The
 * {@code totalIterationsBudget} iteration cap from the request is therefore a safety ceiling, not
 * the primary stopping rule — most runs converge and stop well before it.
 *
 * <p><b>Cancellation:</b> a {@link CancellationToken} is checked between waves; if the calling
 * thread is interrupted while waiting on a wave, in-flight futures are cancelled and the engine
 * returns whatever partial progress has already been safely accumulated (already-completed
 * batches are never lost, since they're already reflected in {@code accumulator}).
 *
 * <p><b>Resumability:</b> when a {@link CheckpointState} is supplied, the engine reconstructs the
 * {@link PathAccumulator} (and its reservoir) from the persisted exact sums and resumes batch
 * submission at {@code checkpoint.nextBatchIndex()} — batches below that index are never
 * resubmitted, so retrying a request can never double-count already-completed work.
 */
@Component
public class MonteCarloEngine {
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_MIN_BATCHES = 4;
    private static final double DEFAULT_CI_THRESHOLD_PCT = 0.5;
    private static final int DEFAULT_RESERVOIR_SIZE = 8000;
    private static final long DEFAULT_BATCH_TIMEOUT_SECONDS = 30;

    private final ExecutorService executor;
    private final AppProperties.Simulation config;

    public MonteCarloEngine(ExecutorService monteCarloExecutor, AppProperties appProperties) {
        this.executor = monteCarloExecutor;
        this.config = appProperties.simulation();
    }

    public EngineResult simulate(PathSimulationSpec spec, CheckpointState resumeFrom, CancellationToken cancellationToken) {
        int batchSize = batchSize();
        int reservoirCapacity = reservoirCapacity();
        int totalBatches = Math.max(1, ceilDiv(spec.totalIterationsBudget(), batchSize));
        int waveSize = waveSize();
        int minBatches = Math.min(totalBatches, minBatches());
        double ciThresholdPct = ciThresholdPct();

        PathAccumulator accumulator = resumeFrom == null
                ? new PathAccumulator(spec.years(), reservoirCapacity)
                : resumeFrom.toAccumulator(spec.years(), reservoirCapacity);
        ConcurrentRunningStats goalStats = new ConcurrentRunningStats();
        int startBatchIndex = resumeFrom == null ? 0 : resumeFrom.nextBatchIndex();
        seedGoalStatsFromCarriedForwardProgress(goalStats, accumulator);

        int batchesCompleted = startBatchIndex;
        boolean convergedEarly = false;

        while (batchesCompleted < totalBatches && !cancellationToken.isCancelled()) {
            int remainingBatches = totalBatches - batchesCompleted;
            int thisWaveSize = Math.min(waveSize, remainingBatches);
            List<Future<BatchTask.Summary>> futures = new ArrayList<>(thisWaveSize);
            for (int w = 0; w < thisWaveSize; w++) {
                int batchIndex = batchesCompleted + w;
                int iterationsInBatch = iterationsForBatch(batchIndex, batchSize, spec.totalIterationsBudget());
                futures.add(executor.submit(new BatchTask(spec, batchIndex, iterationsInBatch, batchSize, accumulator)));
            }

            boolean interrupted = false;
            for (Future<BatchTask.Summary> future : futures) {
                try {
                    BatchTask.Summary summary = future.get(batchTimeoutSeconds(), TimeUnit.SECONDS);
                    if (summary.iterationsRun() > 0) {
                        double mean = summary.goalHits() / (double) summary.iterationsRun();
                        double m2 = summary.iterationsRun() * mean * (1 - mean);
                        goalStats.combine(summary.iterationsRun(), mean, m2);
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    futures.forEach(f -> f.cancel(true));
                    throw new SimulationExecutionException(
                            "Monte Carlo batch failed for path '" + spec.path().name() + "'", e.getCause());
                } catch (TimeoutException e) {
                    futures.forEach(f -> f.cancel(true));
                    throw new SimulationExecutionException(
                            "Monte Carlo batch timed out for path '" + spec.path().name() + "'", e);
                }
            }
            if (interrupted) {
                futures.forEach(f -> f.cancel(true));
                break;
            }

            batchesCompleted += thisWaveSize;

            if (batchesCompleted >= minBatches) {
                ConcurrentRunningStats.Snapshot snapshot = goalStats.snapshot();
                double ciHalfWidthPct = snapshot.ci95HalfWidth() * 100.0;
                if (ciHalfWidthPct <= ciThresholdPct) {
                    convergedEarly = true;
                    break;
                }
            }
        }

        return new EngineResult(accumulator, goalStats.snapshot(), batchesCompleted, totalBatches, convergedEarly);
    }

    /**
     * When resuming, the already-completed iterations carried forward in the accumulator must be
     * reflected in the live convergence statistic too, otherwise the CI check would (incorrectly)
     * judge convergence using only the iterations run since the resume point.
     */
    private void seedGoalStatsFromCarriedForwardProgress(ConcurrentRunningStats goalStats, PathAccumulator accumulator) {
        long n = accumulator.completedIterations();
        if (n > 0) {
            double mean = accumulator.goalHits() / (double) n;
            double m2 = n * mean * (1 - mean);
            goalStats.combine(n, mean, m2);
        }
    }

    private int iterationsForBatch(int batchIndex, int batchSize, int totalIterationsBudget) {
        int start = batchIndex * batchSize;
        return Math.max(0, Math.min(batchSize, totalIterationsBudget - start));
    }

    private int ceilDiv(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    private int batchSize() {
        return config != null && config.batchSize() != null && config.batchSize() > 0 ? config.batchSize() : DEFAULT_BATCH_SIZE;
    }

    private int waveSize() {
        if (config != null && config.waveSize() != null && config.waveSize() > 0) {
            return config.waveSize();
        }
        return EngineExecutorConfig.resolveWorkerThreads(config);
    }

    private int minBatches() {
        return config != null && config.minBatchesBeforeConvergenceCheck() != null && config.minBatchesBeforeConvergenceCheck() > 0
                ? config.minBatchesBeforeConvergenceCheck() : DEFAULT_MIN_BATCHES;
    }

    private double ciThresholdPct() {
        return config != null && config.convergenceCiHalfWidthThresholdPct() != null
                ? config.convergenceCiHalfWidthThresholdPct() : DEFAULT_CI_THRESHOLD_PCT;
    }

    private int reservoirCapacity() {
        return config != null && config.reservoirSize() != null && config.reservoirSize() > 0
                ? config.reservoirSize() : DEFAULT_RESERVOIR_SIZE;
    }

    private long batchTimeoutSeconds() {
        return config != null && config.batchTimeoutSeconds() != null && config.batchTimeoutSeconds() > 0
                ? config.batchTimeoutSeconds() : DEFAULT_BATCH_TIMEOUT_SECONDS;
    }
}
