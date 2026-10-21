package com.umesh.decision.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umesh.decision.api.dto.PathOutcome;
import com.umesh.decision.api.dto.SimulationRequest;
import com.umesh.decision.api.dto.SimulationResponse;
import com.umesh.decision.domain.DecisionScenario;
import com.umesh.decision.domain.SimulationRun;
import com.umesh.decision.domain.SimulationStatus;
import com.umesh.decision.repository.DecisionScenarioRepository;
import com.umesh.decision.repository.SimulationRunRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Use-case coordinator for running a decision simulation: resolves idempotency, owns the short
 * database transactions around the (potentially multi-second) concurrent Monte Carlo compute, and
 * assembles the final response. The compute itself — and all of its concurrency, convergence and
 * resumability logic — lives in {@link SimulationOrchestrator} / {@code com.umesh.decision.engine}.
 *
 * <p><b>Why this class is not {@code @Transactional}:</b> it previously wrapped the entire
 * simulation (a CPU-bound loop that could run for seconds) in one transaction, which means it held
 * a pooled database connection open for the full duration of unrelated CPU work — under any real
 * concurrent load that exhausts the connection pool long before it exhausts the CPU. The two
 * database interactions (create/load the run, then finalize it) are each wrapped in their own
 * short, explicit transaction via {@link TransactionTemplate}; nothing holds a connection open
 * while {@link SimulationOrchestrator} is running batches.
 */
@Service
public class SimulationService {
    private final DecisionScenarioRepository scenarioRepository;
    private final SimulationRunRepository runRepository;
    private final GroqInsightService groqInsightService;
    private final SimulationOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public SimulationService(DecisionScenarioRepository scenarioRepository, SimulationRunRepository runRepository,
                             GroqInsightService groqInsightService, SimulationOrchestrator orchestrator,
                             ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.scenarioRepository = scenarioRepository;
        this.runRepository = runRepository;
        this.groqInsightService = groqInsightService;
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public SimulationResponse runSimulation(SimulationRequest request, String clientIdempotencyKey) {
        if (request.paths().size() < 2) {
            throw new IllegalArgumentException("At least two decision paths are required.");
        }

        String fingerprint = fingerprint(request);
        String idempotencyKey = StringUtils.hasText(clientIdempotencyKey) ? clientIdempotencyKey : fingerprint;

        Optional<SimulationResponse> cached = findCompletedResponse(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        SimulationRun run = loadOrCreateRun(idempotencyKey, request);

        List<PathOutcome> outcomes = request.paths().stream()
                .map(path -> orchestrator.simulatePath(request, path, run, fingerprint))
                .sorted(Comparator.comparingDouble(PathOutcome::goalProbability).reversed())
                .toList();
        String winner = outcomes.get(0).pathName();

        SimulationResponse responseWithoutAi = new SimulationResponse(
                run.getScenario().getId(), run.getId(), request.title(), request.location(), request.years(),
                request.iterations(), outcomes, winner, "", OffsetDateTime.now());
        String aiInsight = groqInsightService.generateInsight(request, responseWithoutAi);
        SimulationResponse response = new SimulationResponse(
                responseWithoutAi.scenarioId(), responseWithoutAi.runId(), responseWithoutAi.title(),
                responseWithoutAi.location(), responseWithoutAi.years(), responseWithoutAi.iterations(),
                responseWithoutAi.outcomes(), responseWithoutAi.winner(), aiInsight, responseWithoutAi.createdAt());

        finalizeRun(run, response, aiInsight);
        return response;
    }

    @Transactional(readOnly = true)
    public List<SimulationResponse> history() {
        return runRepository.findAll().stream()
                .filter(run -> run.getStatus() == SimulationStatus.COMPLETED && run.getSummaryJson() != null)
                .map(run -> fromJson(run.getSummaryJson()))
                .toList();
    }

    /**
     * The only safe cache for idempotent replay: keyed by the full idempotency key (either the
     * caller-supplied {@code Idempotency-Key} or a content fingerprint of the whole request), so a
     * cache hit can only ever occur for a byte-for-byte-equivalent prior request. A previous
     * version of this class cached per-path outcomes keyed by a handful of fields (title, target
     * salary, years, iterations, path name) that excluded calibration and most path parameters —
     * two materially different requests sharing those few fields would silently return each
     * other's cached result. That bug is why idempotency is now keyed explicitly rather than by a
     * partial parameter list.
     */
    @Cacheable(value = "simulation-runs", key = "#idempotencyKey", unless = "#result.isEmpty()")
    public Optional<SimulationResponse> findCompletedResponse(String idempotencyKey) {
        return runRepository.findByIdempotencyKey(idempotencyKey)
                .filter(run -> run.getStatus() == SimulationStatus.COMPLETED && run.getSummaryJson() != null)
                .map(run -> fromJson(run.getSummaryJson()));
    }

    private SimulationRun loadOrCreateRun(String idempotencyKey, SimulationRequest request) {
        Optional<SimulationRun> existing = runRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        DecisionScenario scenario = new DecisionScenario(UUID.randomUUID(), request.title(), request.location(),
                request.currentSalaryLpa(), request.offerSalaryLpa(), request.targetSalaryLpa(), request.years(),
                request.iterations(), request.riskTolerance(), OffsetDateTime.now());
        SimulationRun run = new SimulationRun(UUID.randomUUID(), scenario, SimulationStatus.IN_PROGRESS,
                idempotencyKey, request.iterations(), null, null, OffsetDateTime.now(), null);
        try {
            return transactionTemplate.execute(status -> {
                scenarioRepository.save(scenario);
                return runRepository.save(run);
            });
        } catch (DataIntegrityViolationException raceLost) {
            // A concurrent request with the identical idempotency key won the insert race; use its row.
            return runRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> raceLost);
        }
    }

    private void finalizeRun(SimulationRun run, SimulationResponse response, String aiInsight) {
        SimulationRun completed = new SimulationRun(run.getId(), run.getScenario(), SimulationStatus.COMPLETED,
                run.getIdempotencyKey(), run.getTargetIterations(), toJson(response), aiInsight,
                run.getCreatedAt(), OffsetDateTime.now());
        transactionTemplate.executeWithoutResult(status -> runRepository.save(completed));
    }

    private String fingerprint(SimulationRequest request) {
        try {
            String canonical = objectMapper.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute request fingerprint", e);
        }
    }

    private String toJson(SimulationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize simulation response", e);
        }
    }

    private SimulationResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, SimulationResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to read simulation response", e);
        }
    }
}
