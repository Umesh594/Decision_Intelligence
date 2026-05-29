package com.umesh.decision.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record DecisionPathRequest(
        @NotBlank String name,
        @Positive double startingSalaryLpa,
        @DecimalMin("-0.50") @DecimalMax("1.00") double annualGrowthMean,
        @DecimalMin("0.00") @DecimalMax("1.00") double annualGrowthStdDev,
        @DecimalMin("0.00") @DecimalMax("0.80") double layoffProbability,
        @DecimalMin("0.00") @DecimalMax("1.00") double promotionProbability,
        @DecimalMin("0.00") @DecimalMax("1.50") double promotionSalaryBoost,
        @DecimalMin("0.00") @DecimalMax("1.00") double switchProbability,
        @DecimalMin("0.00") @DecimalMax("2.00") double switchSalaryBoost,
        @DecimalMin("0.00") @DecimalMax("0.60") double costOfLivingAdjustment
) {
}
