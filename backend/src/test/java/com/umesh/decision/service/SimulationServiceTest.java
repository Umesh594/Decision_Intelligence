package com.umesh.decision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umesh.decision.api.SimulationTemplates;
import com.umesh.decision.domain.DecisionScenario;
import com.umesh.decision.repository.DecisionScenarioRepository;
import com.umesh.decision.repository.SimulationRunRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulationServiceTest {

    @Test
    void simulationRanksPathsByGoalProbability() {
        DecisionScenarioRepository scenarioRepository = mock(DecisionScenarioRepository.class);
        SimulationRunRepository runRepository = mock(SimulationRunRepository.class);
        GroqInsightService groqInsightService = mock(GroqInsightService.class);
        when(scenarioRepository.save(org.mockito.ArgumentMatchers.any(DecisionScenario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groqInsightService.generateInsight(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("test insight");

        SimulationService service = new SimulationService(
                scenarioRepository,
                runRepository,
                groqInsightService,
                new CalibrationService(),
                new ObjectMapper().findAndRegisterModules()
        );

        var response = service.runSimulation(SimulationTemplates.careerSwitch());

        assertThat(response.scenarioId()).isInstanceOf(UUID.class);
        assertThat(response.outcomes()).hasSize(2);
        assertThat(response.outcomes().get(0).goalProbability())
                .isGreaterThanOrEqualTo(response.outcomes().get(1).goalProbability());
    }
}
