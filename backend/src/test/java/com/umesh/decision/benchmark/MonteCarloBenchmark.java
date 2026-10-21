package com.umesh.decision.benchmark;

import com.umesh.decision.api.dto.DecisionPathRequest;
import com.umesh.decision.config.AppProperties;
import com.umesh.decision.engine.CancellationToken;
import com.umesh.decision.engine.EngineResult;
import com.umesh.decision.engine.MonteCarloEngine;
import com.umesh.decision.engine.PathSimulationSpec;
import com.umesh.decision.service.CalibrationService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Standalone throughput/latency benchmark for {@link MonteCarloEngine}, run with a plain
 * {@code main()} method rather than JMH to keep the build simple (see the Phase 3 task notes).
 *
 * <p>Run with (from {@code backend/}):
 * <pre>
 * mvn test-compile
 * mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" com.umesh.decision.benchmark.MonteCarloBenchmark
 * </pre>
 *
 * <p>Convergence-based early stopping is deliberately disabled here (CI threshold 0, which can
 * never be satisfied) so every thread-count configuration runs the exact same fixed iteration
 * budget — this isolates the effect of parallelism on throughput from the (unrelated, and
 * separately tested) effect of convergence detection on total work done.
 */
public final class MonteCarloBenchmark {
    private static final int ITERATIONS = 400_000;
    private static final int YEARS = 8;
    private static final int BATCH_SIZE = 500;
    private static final int REPEATS_PER_THREAD_COUNT = 3;

    private MonteCarloBenchmark() {
    }

    public static void main(String[] args) throws InterruptedException {
        DecisionPathRequest path = new DecisionPathRequest(
                "Switch", 12, 0.11, 0.09, 0.06, 0.20, 0.20, 0.22, 0.22, 0.08);
        CalibrationService.CalibratedAssumptions calibration = new CalibrationService().calibrate("Bangalore",
                new com.umesh.decision.api.dto.CalibrationProfile("Junior", "TIER_2", "HIGH", null, null, null, null, null, null));
        PathSimulationSpec spec = new PathSimulationSpec(path, YEARS, 60.0, calibration, "benchmark-fingerprint", ITERATIONS);

        System.out.println("Monte Carlo engine throughput benchmark");
        System.out.println("iterations=" + ITERATIONS + " years=" + YEARS + " batchSize=" + BATCH_SIZE
                + " cpuCores=" + Runtime.getRuntime().availableProcessors());
        System.out.println();

        System.out.println("warm-up (JIT compilation, not counted below):");
        runOnce(spec, 1);
        runOnce(spec, Runtime.getRuntime().availableProcessors());
        System.out.println();

        List<Integer> threadCounts = List.of(1, 2, 4, 8, Runtime.getRuntime().availableProcessors());
        double[] throughputs = new double[threadCounts.size()];
        double[] elapsedMs = new double[threadCounts.size()];
        for (int i = 0; i < threadCounts.size(); i++) {
            int threads = threadCounts.get(i);
            double bestElapsed = Double.MAX_VALUE;
            double bestThroughput = 0;
            for (int repeat = 0; repeat < REPEATS_PER_THREAD_COUNT; repeat++) {
                Result result = runOnce(spec, threads);
                if (result.elapsedMs() < bestElapsed) {
                    bestElapsed = result.elapsedMs();
                    bestThroughput = result.throughput();
                }
            }
            throughputs[i] = bestThroughput;
            elapsedMs[i] = bestElapsed;
        }

        System.out.println();
        System.out.printf("%-10s %-14s %-18s %-14s%n", "threads", "elapsedMs", "iterations/sec", "speedup-vs-1t");
        double baseline = throughputs[0];
        for (int i = 0; i < threadCounts.size(); i++) {
            System.out.printf("%-10d %-14.1f %-18.1f %-14.2fx%n",
                    threadCounts.get(i), elapsedMs[i], throughputs[i], throughputs[i] / baseline);
        }
    }

    private record Result(double elapsedMs, double throughput) {
    }

    private static Result runOnce(PathSimulationSpec spec, int threads) throws InterruptedException {
        AppProperties.Simulation config = new AppProperties.Simulation(
                threads, 256, BATCH_SIZE, threads, Integer.MAX_VALUE, 0.0, 8000, 60L);
        AppProperties appProperties = new AppProperties(null, null, null, config, null);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            MonteCarloEngine engine = new MonteCarloEngine(executor, appProperties);
            long start = System.nanoTime();
            EngineResult result = engine.simulate(spec, null, CancellationToken.none());
            long elapsedNanos = System.nanoTime() - start;
            double elapsedMs = elapsedNanos / 1_000_000.0;
            double throughput = result.accumulator().completedIterations() / (elapsedNanos / 1_000_000_000.0);
            System.out.printf(
                    "  -> threads=%-3d completed=%-8d elapsedMs=%-10.1f throughput=%-12.1f converged=%s%n",
                    threads, result.accumulator().completedIterations(), elapsedMs, throughput, result.convergedEarly());
            return new Result(elapsedMs, throughput);
        } finally {
            executor.shutdown();
            executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
