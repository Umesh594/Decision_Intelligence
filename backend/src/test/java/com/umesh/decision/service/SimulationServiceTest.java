package com.umesh.decision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umesh.decision.api.SimulationTemplates;
import com.umesh.decision.config.AppProperties;
import com.umesh.decision.domain.DecisionScenario;
import com.umesh.decision.domain.SimulationCheckpoint;
import com.umesh.decision.domain.SimulationRun;
import com.umesh.decision.engine.MonteCarloEngine;
import com.umesh.decision.repository.DecisionScenarioRepository;
import com.umesh.decision.repository.SimulationCheckpointRepository;
import com.umesh.decision.repository.SimulationRunRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SimulationServiceTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void simulationRanksPathsByGoalProbability() {
        SimulationService service = buildService();

        var response = service.runSimulation(SimulationTemplates.careerSwitch(), null);

        assertThat(response.scenarioId()).isInstanceOf(UUID.class);
        assertThat(response.outcomes()).hasSize(2);
        assertThat(response.outcomes().get(0).goalProbability())
                .isGreaterThanOrEqualTo(response.outcomes().get(1).goalProbability());
        assertThat(response.outcomes()).allSatisfy(outcome -> assertThat(outcome.completedIterations()).isGreaterThan(0));
    }

    @Test
    void retryingTheSameRequestReturnsTheSameRunWithoutRecomputing() {
        SimulationService service = buildService();
        var request = SimulationTemplates.careerSwitch();

        var first = service.runSimulation(request, "fixed-key-123");
        var second = service.runSimulation(request, "fixed-key-123");

        assertThat(second.runId()).isEqualTo(first.runId());
        assertThat(second.outcomes()).isEqualTo(first.outcomes());
    }

    @Test
    void identicalRequestsWithoutAnExplicitKeyAreStillIdempotentViaContentFingerprint() {
        SimulationService service = buildService();
        var request = SimulationTemplates.careerSwitch();

        var first = service.runSimulation(request, null);
        var second = service.runSimulation(request, null);

        assertThat(second.runId()).isEqualTo(first.runId());
    }

    private SimulationService buildService() {
        DecisionScenarioRepository scenarioRepository = mock(DecisionScenarioRepository.class);
        SimulationRunRepository runRepository = mock(SimulationRunRepository.class);
        SimulationCheckpointRepository checkpointRepository = mock(SimulationCheckpointRepository.class);
        GroqInsightService groqInsightService = mock(GroqInsightService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        Map<UUID, SimulationRun> runsById = new ConcurrentHashMap<>();
        when(scenarioRepository.save(any(DecisionScenario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.save(any(SimulationRun.class))).thenAnswer(invocation -> {
            SimulationRun run = invocation.getArgument(0);
            runsById.put(run.getId(), run);
            return run;
        });
        when(runRepository.findByIdempotencyKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return runsById.values().stream().filter(run -> key.equals(run.getIdempotencyKey())).findFirst();
        });
        when(checkpointRepository.findByRunIdAndPathName(any(), any())).thenReturn(Optional.empty());
        when(checkpointRepository.save(any(SimulationCheckpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(groqInsightService.generateInsight(any(), any())).thenReturn("test insight");
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MonteCarloEngine engine = new MonteCarloEngine(executor, testAppProperties());
        CalibrationService calibrationService = new CalibrationService();
        SimulationOrchestrator orchestrator = new SimulationOrchestrator(
                engine, calibrationService, checkpointRepository, objectMapper,
                new TransactionTemplate(transactionManager));

        return new SimulationService(scenarioRepository, runRepository, groqInsightService, orchestrator,
                objectMapper, transactionManager);
    }

    static AppProperties testAppProperties() {
        AppProperties.Simulation simulation = new AppProperties.Simulation(
                2, 16, 200, 2, 2, 2.0, 2000, 10L);
        return new AppProperties(null, null, null, simulation, null);
    }
}
