package com.umesh.decision.engine;

import com.umesh.decision.api.dto.DecisionPathRequest;
import com.umesh.decision.service.CalibrationService.CalibratedAssumptions;

/** Immutable description of the work the engine must do for one decision path. */
public record PathSimulationSpec(
        DecisionPathRequest path,
        int years,
        double targetSalaryLpa,
        CalibratedAssumptions calibration,
        String runFingerprint,
        int totalIterationsBudget
) {
}
