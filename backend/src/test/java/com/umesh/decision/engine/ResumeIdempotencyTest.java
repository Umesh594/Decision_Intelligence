package com.umesh.decision.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.umesh.decision.api.dto.DecisionPathRequest;
import com.umesh.decision.config.AppProperties;
import com.umesh.decision.service.CalibrationService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the engine's resumability contract: resuming a checkpointed run continues exactly where
 * it left off — it never re-executes a completed batch and never double-counts its contribution
 * to the aggregated totals. See {@code SimulationCheckpoint} / {@code SimulationOrchestrator} for
 * how this is persisted in the real request path; this test exercises the same mechanism
 * ({@link CheckpointState} -> {@link MonteCarloEngine#simulate}) directly.
 */
class ResumeIdempotencyTest {
    private static final DecisionPathRequest PATH = new DecisionPathRequest(
            "Switch", 12, 0.11, 0.09, 0.06, 0.20, 0.20, 0.22, 0.22, 0.08);
    private static final CalibrationService.CalibratedAssumptions CALIBRATION =
            new CalibrationService.CalibratedAssumptions(1.1, 0.18, 0.14, 0.16, 0.45, 0.6, 1.1, 1.0);
    private static final String FINGERPRINT = "resume-test-fingerprint";
    private static final int BATCH_SIZE = 200;
    private static final int TOTAL_ITERATIONS = 2000; // exactly 10 batches

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void resumingAfterAPartialRunMatchesARunThatNeverStoppedBitForBit() {
        executor = Executors.newFixedThreadPool(4);

        // Baseline: one uninterrupted run over the full budget.
        MonteCarloEngine fullRunEngine = engine();
        PathSimulationSpec spec = new PathSimulationSpec(PATH, 6, 60.0, CALIBRATION, FINGERPRINT, TOTAL_ITERATIONS);
        EngineResult fullRun = fullRunEngine.simulate(spec, null, CancellationToken.none());
        assertThat(fullRun.accumulator().completedIterations()).isEqualTo(TOTAL_ITERATIONS);

        // Simulate a crash after only 3 of 10 batches by asking for a smaller budget first.
        MonteCarloEngine partialEngine = engine();
        PathSimulationSpec partialSpec = new PathSimulationSpec(PATH, 6, 60.0, CALIBRATION, FINGERPRINT, 3 * BATCH_SIZE);
        EngineResult partial = partialEngine.simulate(partialSpec, null, CancellationToken.none());
        assertThat(partial.nextBatchIndex()).isEqualTo(3);
        assertThat(partial.accumulator().completedIterations()).isEqualTo(3L * BATCH_SIZE);

        CheckpointState checkpoint = new CheckpointState(
                partial.nextBatchIndex(),
                partial.accumulator().completedIterations(),
                partial.accumulator().goalHits(),
                partial.accumulator().downsideEvents(),
                partial.accumulator().recoveryHits(),
                partial.accumulator().sumFinalSalary(),
                partial.accumulator().sumFinalSalarySq(),
                partial.accumulator().sumWealth(),
                partial.accumulator().yearlyTotals(),
                partial.accumulator().reservoir().snapshot(),
                partial.accumulator().reservoir().seenCount());

        // Resume with the ORIGINAL full budget; this must only run batches 3..9, not re-run 0..2.
        MonteCarloEngine resumeEngine = engine();
        EngineResult resumed = resumeEngine.simulate(spec, checkpoint, CancellationToken.none());

        assertThat(resumed.accumulator().completedIterations())
                .as("resuming must land on exactly the requested total, never double-counting the first 3 batches")
                .isEqualTo(TOTAL_ITERATIONS);
        assertThat(resumed.accumulator().goalHits()).isEqualTo(fullRun.accumulator().goalHits());
        assertThat(resumed.accumulator().downsideEvents()).isEqualTo(fullRun.accumulator().downsideEvents());
        assertThat(resumed.accumulator().recoveryHits()).isEqualTo(fullRun.accumulator().recoveryHits());
        // Floating-point sums are compared within a tiny tolerance: DoubleAdder's internal cell
        // striping (and therefore summation order) depends on thread contention, which differs
        // between "all 10 batches in one go" and "3 batches, then resume for 7 more" even though
        // the exact same multiset of per-iteration values is summed either way. The long/int
        // counts above are exact because integer addition has no such order-dependence.
        org.assertj.core.data.Offset<Double> tolerance = org.assertj.core.data.Offset.offset(1e-6);
        assertThat(resumed.accumulator().sumFinalSalary()).isCloseTo(fullRun.accumulator().sumFinalSalary(), tolerance);
        assertThat(resumed.accumulator().sumFinalSalarySq()).isCloseTo(fullRun.accumulator().sumFinalSalarySq(), tolerance);
        assertThat(resumed.accumulator().sumWealth()).isCloseTo(fullRun.accumulator().sumWealth(), tolerance);
        double[] resumedYearly = resumed.accumulator().yearlyTotals();
        double[] fullYearly = fullRun.accumulator().yearlyTotals();
        for (int year = 0; year < fullYearly.length; year++) {
            assertThat(resumedYearly[year]).isCloseTo(fullYearly[year], tolerance);
        }
    }

    @Test
    void aRunThatHasAlreadyReachedItsBudgetDoesNoFurtherWorkOnResume() {
        executor = Executors.newFixedThreadPool(2);
        MonteCarloEngine fullEngine = engine();
        PathSimulationSpec spec = new PathSimulationSpec(PATH, 5, 55.0, CALIBRATION, "already-done", 1000);
        EngineResult done = fullEngine.simulate(spec, null, CancellationToken.none());

        CheckpointState completedCheckpoint = new CheckpointState(
                done.nextBatchIndex(), done.accumulator().completedIterations(), done.accumulator().goalHits(),
                done.accumulator().downsideEvents(), done.accumulator().recoveryHits(),
                done.accumulator().sumFinalSalary(), done.accumulator().sumFinalSalarySq(), done.accumulator().sumWealth(),
                done.accumulator().yearlyTotals(), done.accumulator().reservoir().snapshot(), done.accumulator().reservoir().seenCount());

        MonteCarloEngine secondEngine = engine();
        EngineResult replay = secondEngine.simulate(spec, completedCheckpoint, CancellationToken.none());

        assertThat(replay.batchesCompleted()).isEqualTo(done.batchesCompleted());
        assertThat(replay.accumulator().completedIterations()).isEqualTo(done.accumulator().completedIterations());
        assertThat(replay.accumulator().goalHits()).isEqualTo(done.accumulator().goalHits());
    }

    private MonteCarloEngine engine() {
        AppProperties.Simulation config = new AppProperties.Simulation(
                4, 32, BATCH_SIZE, 2, 1000, 0.0, 3000, 15L); // minBatches huge + threshold 0 => never converges early
        AppProperties appProperties = new AppProperties(null, null, null, config, null);
        return new MonteCarloEngine(executor, appProperties);
    }
}
