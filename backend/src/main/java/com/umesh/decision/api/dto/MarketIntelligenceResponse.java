package com.umesh.decision.api.dto;

import java.util.List;

public record MarketIntelligenceResponse(
        String role,
        String location,
        String roleLevel,
        String companyTier,
        double p25SalaryLpa,
        double medianSalaryLpa,
        double p75SalaryLpa,
        double p90SalaryLpa,
        double marketDemandScore,
        double cityCostIndex,
        double interviewConversionRate,
        double historicalSalaryGrowth,
        double marketVolatility,
        double skillDemandScore,
        double confidenceScore,
        CalibrationProfile recommendedCalibration,
        List<String> signals,
        List<String> dataSources
) {
}
