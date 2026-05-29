package com.umesh.decision.api;

import com.umesh.decision.api.dto.DecisionPathRequest;
import com.umesh.decision.api.dto.CalibrationProfile;
import com.umesh.decision.api.dto.SimulationRequest;
import com.umesh.decision.domain.RiskTolerance;
import java.math.BigDecimal;
import java.util.List;

public final class SimulationTemplates {
    private SimulationTemplates() {
    }

    public static SimulationRequest careerSwitch() {
        return new SimulationRequest(
                "Should I switch jobs?",
                BigDecimal.valueOf(8),
                BigDecimal.valueOf(12),
                "Bangalore",
                BigDecimal.valueOf(25),
                4,
                5000,
                RiskTolerance.MEDIUM,
                new CalibrationProfile("Junior", "TIER_2", "HIGH", 1.12, 0.18, 0.14, 0.16, 0.45, 0.82),
                List.of(
                        new DecisionPathRequest("Stay", 8, 0.12, 0.07, 0.04, 0.18, 0.18, 0.18, 0.25, 0.06),
                        new DecisionPathRequest("Switch", 12, 0.10, 0.08, 0.07, 0.22, 0.20, 0.22, 0.22, 0.08)
                )
        );
    }
}
