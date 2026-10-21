package com.umesh.decision.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umesh.decision.api.dto.DecisionPathRequest;
import com.umesh.decision.api.dto.PathOutcome;
import com.umesh.decision.api.dto.SimulationRequest;
import com.umesh.decision.domain.SimulationCheckpoint;
import com.umesh.decision.domain.SimulationRun;
import com.umesh.decision.engine.CancellationToken;
import com.umesh.decision.engine.CheckpointState;
import com.umesh.decision.engine.EngineResult;
import com.umesh.decision.engine.MonteCarloEngine;
import com.umesh.decision.engine.PathAccumulator;
import com.umesh.decision.engine.PathSimulationSpec;
import com.umesh.decision.repository.SimulationCheckpointRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bridges one decision path's request parameters to the concurrent {@link MonteCarloEngine},
 * persists its checkpoint so the run is resumable, and turns the engine's exact aggregates into
 * the public {@link PathOutcome} shape.
 *
 * <p>Checkpoint reads/writes are each wrapped in their own short, explicit transaction (via
 * {@link TransactionTemplate}) rather than letting the caller's transaction span the whole
 * simulation — the heavy CPU-bound work in between intentionally holds no database connection or
 * transaction open (see {@link SimulationService} for why that matters under load).
 */
@Component
public class SimulationOrchestrator {
    private final MonteCarloEngine engine;
    private final CalibrationService calibrationService;
    private final SimulationCheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public SimulationOrchestrator(MonteCarloEngine engine, CalibrationService calibrationService,
                                  SimulationCheckpointRepository checkpointRepository, ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate) {
        this.engine = engine;
        this.calibrationService = calibrationService;
        this.checkpointRepository = checkpointRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public PathOutcome simulatePath(SimulationRequest request, DecisionPathRequest path, SimulationRun run, String runFingerprint) {
        CalibrationService.CalibratedAssumptions calibration = calibrationService.calibrate(request.location(), request.calibration());
        PathSimulationSpec spec = new PathSimulationSpec(path, request.years(), request.targetSalaryLpa().doubleValue(),
                calibration, runFingerprint, request.iterations());

        Optional<SimulationCheckpoint> existingCheckpoint = transactionTemplate.execute(status ->
                checkpointRepository.findByRunIdAndPathName(run.getId(), path.name()));
        CheckpointState resumeFrom = existingCheckpoint.map(this::toCheckpointState).orElse(null);

        EngineResult result = engine.simulate(spec, resumeFrom, CancellationToken.none());

        UUID checkpointId = existingCheckpoint.map(SimulationCheckpoint::getId).orElseGet(UUID::randomUUID);
        persistCheckpoint(checkpointId, run, path.name(), result);

        return toPathOutcome(path.name(), request, result);
    }

    private void persistCheckpoint(UUID checkpointId, SimulationRun run, String pathName, EngineResult result) {
        PathAccumulator acc = result.accumulator();
        String yearlyTotalsJson = writeJson(acc.yearlyTotals());
        String reservoirJson = writeJson(acc.reservoir().snapshot());
        SimulationCheckpoint checkpoint = new SimulationCheckpoint(
                checkpointId, run, pathName, result.nextBatchIndex(), acc.completedIterations(), acc.goalHits(),
                acc.downsideEvents(), acc.recoveryHits(), acc.sumFinalSalary(), acc.sumFinalSalarySq(), acc.sumWealth(),
                yearlyTotalsJson, reservoirJson, acc.reservoir().seenCount(), OffsetDateTime.now());
        transactionTemplate.executeWithoutResult(status -> checkpointRepository.save(checkpoint));
    }

    private CheckpointState toCheckpointState(SimulationCheckpoint checkpoint) {
        return new CheckpointState(
                checkpoint.getNextBatchIndex(),
                checkpoint.getCompletedIterations(),
                checkpoint.getGoalHits(),
                checkpoint.getDownsideEvents(),
                checkpoint.getRecoveryHits(),
                checkpoint.getSumFinalSalary(),
                checkpoint.getSumFinalSalarySq(),
                checkpoint.getSumWealth(),
                readDoubleArray(checkpoint.getYearlyTotalsJson()),
                readDoubleArray(checkpoint.getReservoirJson()),
                checkpoint.getReservoirSeenCount());
    }

    private PathOutcome toPathOutcome(String pathName, SimulationRequest request, EngineResult result) {
        PathAccumulator acc = result.accumulator();
        long n = Math.max(1, acc.completedIterations());
        double targetSalary = request.targetSalaryLpa().doubleValue();

        double[] reservoirSorted = acc.reservoir().snapshot();
        Arrays.sort(reservoirSorted);
        double medianFinal = percentile(reservoirSorted, 0.50);
        double p10 = percentile(reservoirSorted, 0.10);
        double p90 = percentile(reservoirSorted, 0.90);

        double volatility = Math.sqrt(acc.variance());
        double downsideRisk = acc.downsideEvents() * 100.0 / (n * (double) request.years());
        double resilience = acc.downsideEvents() == 0 ? 100.0 : acc.recoveryHits() * 100.0 / acc.downsideEvents();
        double upside = Math.max(0, (p90 - targetSalary) * 100.0 / targetSalary);
        double goalProbability = acc.goalHits() * 100.0 / n;
        double targetProgress = clamp(medianFinal * 100.0 / targetSalary, 0, 100);

        double riskPenalty = switch (request.riskTolerance()) {
            case LOW -> downsideRisk * 1.35 + volatility * 1.10;
            case MEDIUM -> downsideRisk + volatility * 0.85;
            case HIGH -> downsideRisk * 0.65 + volatility * 0.55;
        };
        double riskAdjustedScore = clamp(
                goalProbability * 0.42 + targetProgress * 0.34 + resilience * 0.14 + upside * 0.10 - riskPenalty * 0.45,
                0, 100);

        // Genuine statistical confidence: derived from the actually-achieved 95% CI half-width on
        // the goal-probability estimate, not a formula based only on the requested iteration count.
        double ciHalfWidthPct = result.goalProbabilityStats().ci95HalfWidth() * 100.0;
        double confidenceScore = clamp(100 - ciHalfWidthPct, 35, 99);

        double[] yearlyTotals = acc.yearlyTotals();
        var medianByYear = new java.util.ArrayList<Double>(yearlyTotals.length);
        for (double total : yearlyTotals) {
            medianByYear.add(round(total / n));
        }

        return new PathOutcome(
                pathName,
                round(goalProbability),
                round(medianFinal),
                round(p10),
                round(p90),
                round(acc.sumWealth() / n),
                round(downsideRisk),
                round(volatility),
                round(riskAdjustedScore),
                round(targetProgress),
                round(resilience),
                round(upside),
                round(confidenceScore),
                medianByYear,
                acc.completedIterations(),
                result.convergedEarly()
        );
    }

    private double percentile(double[] sorted, double p) {
        if (sorted.length == 0) {
            return 0.0;
        }
        int index = Math.min(sorted.length - 1, Math.max(0, (int) Math.round(p * (sorted.length - 1))));
        return sorted[index];
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String writeJson(double[] values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize checkpoint array", e);
        }
    }

    private double[] readDoubleArray(String json) {
        if (json == null || json.isBlank()) {
            return new double[0];
        }
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize checkpoint array", e);
        }
    }
}
