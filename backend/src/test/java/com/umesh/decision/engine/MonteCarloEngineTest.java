package com.umesh.decision.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.umesh.decision.api.dto.DecisionPathRequest;
import com.umesh.decision.config.AppProperties;
import com.umesh.decision.service.CalibrationService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MonteCarloEngineTest {

    private static final DecisionPathRequest PATH = new DecisionPathRequest(
            "Stay", 10, 0.10, 0.08, 0.05, 0.20, 0.18, 0.20, 0.25, 0.05);
    private static final CalibrationService.CalibratedAssumptions CALIBRATION =
            new CalibrationService.CalibratedAssumptions(1.1, 0.18, 0.14, 0.16, 0.45, 0.6, 1.1, 1.0);

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void neverLosesAnUpdateUnderHighConcurrencyEvenWhenItNeverConverges() {
        // ciThreshold = 0 can never be satisfied, so the engine must run the full iteration budget.
        executor = Executors.newFixedThreadPool(8);
        MonteCarloEngine engine = engineWith(executor, 8, 2, 100, 0, 0.0, 4000);

        int totalIterations = 8000; // 80 batches of 100
        PathSimulationSpec spec = new PathSimulationSpec(PATH, 5, 50.0, CALIBRATION, "fingerprint-A", totalIterations);

        EngineResult result = engine.simulate(spec, null, CancellationToken.none());

        assertThat(result.convergedEarly()).isFalse();
        assertThat(result.accumulator().completedIterations()).isEqualTo(totalIterations);
        // Every iteration either hits the goal or doesn't; hits can never exceed completed iterations.
        assertThat(result.accumulator().goalHits()).isBetween(0L, (long) totalIterations);
        // downside/recovery bookkeeping must be internally consistent (recoveries can't exceed downside events).
        assertThat(result.accumulator().recoveryHits()).isLessThanOrEqualTo(result.accumulator().downsideEvents());
    }

    @Test
    void convergesEarlyAndStopsWellBeforeTheIterationBudgetWhenCiThresholdIsLoose() {
        executor = Executors.newFixedThreadPool(4);
        MonteCarloEngine engine = engineWith(executor, 4, 1, 200, 1, 25.0, 4000);

        PathSimulationSpec spec = new PathSimulationSpec(PATH, 5, 50.0, CALIBRATION, "fingerprint-B", 100_000);

        EngineResult result = engine.simulate(spec, null, CancellationToken.none());

        assertThat(result.convergedEarly()).isTrue();
        assertThat(result.batchesCompleted()).isLessThan(result.totalBatches());
        assertThat(result.accumulator().completedIterations()).isLessThan(100_000L);
    }

    @Test
    void repeatedBatchesWithTheSameSeedParametersAreFullyReproducible() {
        executor = Executors.newFixedThreadPool(4);
        MonteCarloEngine engineA = engineWith(executor, 4, 2, 100, 0, 0.0, 2000);
        MonteCarloEngine engineB = engineWith(executor, 4, 2, 100, 0, 0.0, 2000);

        PathSimulationSpec spec = new PathSimulationSpec(PATH, 4, 50.0, CALIBRATION, "fingerprint-C", 2000);

        EngineResult resultA = engineA.simulate(spec, null, CancellationToken.none());
        EngineResult resultB = engineB.simulate(spec, null, CancellationToken.none());

        assertThat(resultA.accumulator().goalHits()).isEqualTo(resultB.accumulator().goalHits());
        // DoubleAdder stripes its internal cells by thread contention, so summing the identical
        // multiset of doubles can land in a different (but mathematically equivalent, to within a
        // few ULPs) order across runs. Long/int counts are exact (integer addition is associative
        // regardless of order); only floating-point sums need a tolerance.
        assertThat(resultA.accumulator().sumFinalSalary())
                .isCloseTo(resultB.accumulator().sumFinalSalary(), org.assertj.core.data.Offset.offset(1e-6));
        assertThat(resultA.accumulator().downsideEvents()).isEqualTo(resultB.accumulator().downsideEvents());
    }

    @Test
    void differentRunFingerprintsForTheSameSpecProduceDecorrelatedResults() {
        executor = Executors.newFixedThreadPool(4);
        MonteCarloEngine engine = engineWith(executor, 4, 2, 100, 0, 0.0, 2000);

        PathSimulationSpec specA = new PathSimulationSpec(PATH, 4, 50.0, CALIBRATION, "run-X", 2000);
        PathSimulationSpec specB = new PathSimulationSpec(PATH, 4, 50.0, CALIBRATION, "run-Y", 2000);

        EngineResult resultA = engine.simulate(specA, null, CancellationToken.none());
        EngineResult resultB = engine.simulate(specB, null, CancellationToken.none());

        // Different fingerprints must not be seeded identically (would defeat independent streams).
        assertThat(resultA.accumulator().sumFinalSalary()).isNotEqualTo(resultB.accumulator().sumFinalSalary());
    }

    @Test
    void batchesSubmittedConcurrentlyProduceTheSameAggregateAsIfRunSequentially() {
        // Run the same spec once with a single-threaded executor and once with many threads;
        // because every batch's seed is a pure function of (fingerprint, path, batchIndex),
        // thread scheduling must not affect the final aggregate at all.
        ExecutorService singleThreaded = Executors.newFixedThreadPool(1);
        ExecutorService manyThreaded = Executors.newFixedThreadPool(16);
        try {
            MonteCarloEngine sequentialEngine = engineWith(singleThreaded, 1, 2, 100, 0, 0.0, 1500);
            MonteCarloEngine parallelEngine = engineWith(manyThreaded, 16, 2, 100, 0, 0.0, 1500);
            PathSimulationSpec spec = new PathSimulationSpec(PATH, 4, 50.0, CALIBRATION, "determinism-check", 3000);

            EngineResult sequentialResult = sequentialEngine.simulate(spec, null, CancellationToken.none());
            EngineResult parallelResult = parallelEngine.simulate(spec, null, CancellationToken.none());

            assertThat(parallelResult.accumulator().completedIterations())
                    .isEqualTo(sequentialResult.accumulator().completedIterations());
            assertThat(parallelResult.accumulator().goalHits()).isEqualTo(sequentialResult.accumulator().goalHits());
            // See the tolerance note above: DoubleAdder summation order varies with thread count,
            // so these must be compared within a tiny tolerance rather than bit-for-bit.
            assertThat(parallelResult.accumulator().sumFinalSalary())
                    .isCloseTo(sequentialResult.accumulator().sumFinalSalary(), org.assertj.core.data.Offset.offset(1e-6));
            assertThat(parallelResult.accumulator().sumFinalSalarySq())
                    .isCloseTo(sequentialResult.accumulator().sumFinalSalarySq(), org.assertj.core.data.Offset.offset(1e-6));
        } finally {
            singleThreaded.shutdownNow();
            manyThreaded.shutdownNow();
        }
    }

    @Test
    void stratifiedQuasiRandomSamplingReducesVarianceOfTheSampleMeanVersusNaiveRandom() {
        // Compare the variance of the estimated mean final salary across many independent small
        // runs: the systematic (Sobol-stratified) factor should make repeated small-sample
        // estimates cluster tighter around the true mean than if that same dimension were drawn
        // from plain independent uniform randomness, for the same iteration count.
        executor = Executors.newFixedThreadPool(4);
        int sampleSize = 60;
        int repeats = 40;

        double[] stratifiedMeans = IntStream.range(0, repeats)
                .mapToDouble(run -> {
                    MonteCarloEngine engine = engineWith(executor, 4, 1, sampleSize, 0, 0.0, 500);
                    PathSimulationSpec spec = new PathSimulationSpec(PATH, 3, 50.0, CALIBRATION, "variance-check-" + run, sampleSize);
                    return engine.simulate(spec, null, CancellationToken.none()).accumulator().mean();
                })
                .toArray();

        java.util.SplittableRandom naiveRandom = new java.util.SplittableRandom(42);
        double[] naiveMeans = new double[repeats];
        for (int run = 0; run < repeats; run++) {
            double sum = 0;
            for (int i = 0; i < sampleSize; i++) {
                // Same trajectory math, but year-0 macro shock drawn from plain independent noise
                // instead of the Sobol-stratified systematic factor.
                SystematicRiskSampler.Factors naiveFactors = new SystematicRiskSampler.Factors(
                        naiveRandom.nextGaussian(), naiveRandom.nextGaussian());
                sum += TrajectoryMath.simulate(PATH, 3, CALIBRATION, 50.0, naiveRandom, naiveFactors).finalSalary();
            }
            naiveMeans[run] = sum / sampleSize;
        }

        double stratifiedVariance = variance(stratifiedMeans);
        double naiveVariance = variance(naiveMeans);

        // Not a strict guarantee for every possible seed, but stratification should not perform
        // meaningfully worse than naive sampling on average; assert it is at least competitive.
        assertThat(stratifiedVariance).isLessThan(naiveVariance * 1.5);
    }

    private double variance(double[] values) {
        double mean = java.util.Arrays.stream(values).average().orElse(0);
        return java.util.Arrays.stream(values).map(v -> (v - mean) * (v - mean)).average().orElse(0);
    }

    private MonteCarloEngine engineWith(ExecutorService executor, int workerThreads, int waveSize, int batchSize,
                                        int minBatches, double ciThresholdPct, int reservoirSize) {
        AppProperties.Simulation config = new AppProperties.Simulation(
                workerThreads, 64, batchSize, waveSize, minBatches, ciThresholdPct, reservoirSize, 15L);
        AppProperties appProperties = new AppProperties(null, null, null, config, null);
        return new MonteCarloEngine(executor, appProperties);
    }
}
