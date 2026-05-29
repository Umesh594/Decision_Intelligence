package com.umesh.decision.api.dto;

import java.util.List;

public record PathOutcome(
        String pathName,
        double goalProbability,
        double medianFinalSalaryLpa,
        double p10FinalSalaryLpa,
        double p90FinalSalaryLpa,
        double expectedWealthLpa,
        double downsideRisk,
        double volatility,
        double riskAdjustedScore,
        double targetProgressScore,
        double resilienceScore,
        double upsideScore,
        double confidenceScore,
        List<Double> medianSalaryByYear
) {
}
