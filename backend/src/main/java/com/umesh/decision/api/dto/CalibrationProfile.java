package com.umesh.decision.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record CalibrationProfile(
        String roleLevel,
        String companyTier,
        String marketDemand,
        @DecimalMin("0.50") @DecimalMax("2.00") Double cityCostIndex,
        @DecimalMin("0.00") @DecimalMax("1.00") Double interviewConversionRate,
        @DecimalMin("-0.30") @DecimalMax("1.00") Double historicalSalaryGrowth,
        @DecimalMin("0.00") @DecimalMax("1.00") Double marketVolatility,
        @DecimalMin("0.00") @DecimalMax("1.00") Double emergencyFundScore,
        @DecimalMin("0.00") @DecimalMax("1.00") Double skillDemandScore
) {
}
