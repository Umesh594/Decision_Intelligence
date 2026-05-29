package com.umesh.decision.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record MarketCalibrationRequest(
        @NotBlank String role,
        @NotBlank String location,
        String roleLevel,
        String companyTier,
        List<String> skills
) {
}
