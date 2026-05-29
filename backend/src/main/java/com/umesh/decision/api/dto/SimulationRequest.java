package com.umesh.decision.api.dto;

import com.umesh.decision.domain.RiskTolerance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public record SimulationRequest(
        @NotBlank String title,
        @Positive BigDecimal currentSalaryLpa,
        @Positive BigDecimal offerSalaryLpa,
        @NotBlank String location,
        @Positive BigDecimal targetSalaryLpa,
        @Min(1) @Max(15) int years,
        @Min(1000) @Max(100000) int iterations,
        @NotNull RiskTolerance riskTolerance,
        @Valid CalibrationProfile calibration,
        @NotEmpty List<@Valid DecisionPathRequest> paths
) {
}
