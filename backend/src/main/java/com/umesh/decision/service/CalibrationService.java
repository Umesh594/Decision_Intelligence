package com.umesh.decision.service;

import com.umesh.decision.api.dto.CalibrationProfile;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CalibrationService {
    private static final Map<String, Double> CITY_COST_INDEX = Map.ofEntries(
            Map.entry("bangalore", 1.18),
            Map.entry("bengaluru", 1.18),
            Map.entry("hyderabad", 1.08),
            Map.entry("pune", 1.05),
            Map.entry("mumbai", 1.28),
            Map.entry("delhi", 1.16),
            Map.entry("gurgaon", 1.18),
            Map.entry("chennai", 1.02),
            Map.entry("nagpur", 0.86),
            Map.entry("remote", 0.92)
    );

    public CalibratedAssumptions calibrate(String location, CalibrationProfile profile) {
        CalibrationProfile safe = profile == null
                ? new CalibrationProfile("Junior", "TIER_2", "MEDIUM", null, null, null, null, null, null)
                : profile;
        double cityCost = valueOrDefault(safe.cityCostIndex(), CITY_COST_INDEX.getOrDefault(normalize(location), 1.0));
        double conversion = valueOrDefault(safe.interviewConversionRate(), defaultConversion(safe.roleLevel()));
        double growth = valueOrDefault(safe.historicalSalaryGrowth(), defaultGrowth(safe.roleLevel(), safe.marketDemand()));
        double volatility = valueOrDefault(safe.marketVolatility(), defaultVolatility(safe.marketDemand()));
        double emergencyFund = valueOrDefault(safe.emergencyFundScore(), 0.45);
        double skillDemand = valueOrDefault(safe.skillDemandScore(), defaultSkillDemand(safe.marketDemand()));
        double tierMultiplier = companyTierMultiplier(safe.companyTier());
        double demandMultiplier = marketDemandMultiplier(safe.marketDemand());
        return new CalibratedAssumptions(cityCost, conversion, growth, volatility, emergencyFund, skillDemand, tierMultiplier, demandMultiplier);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private double valueOrDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private double defaultConversion(String roleLevel) {
        return switch (normalize(roleLevel)) {
            case "intern", "fresher" -> 0.12;
            case "junior" -> 0.18;
            case "mid" -> 0.22;
            case "senior" -> 0.16;
            default -> 0.18;
        };
    }

    private double defaultGrowth(String roleLevel, String demand) {
        double base = switch (normalize(roleLevel)) {
            case "intern", "fresher" -> 0.20;
            case "junior" -> 0.16;
            case "mid" -> 0.12;
            case "senior" -> 0.09;
            default -> 0.13;
        };
        return base * marketDemandMultiplier(demand);
    }

    private double defaultVolatility(String demand) {
        return switch (normalize(demand)) {
            case "low" -> 0.10;
            case "high" -> 0.18;
            case "very_high" -> 0.22;
            default -> 0.14;
        };
    }

    private double defaultSkillDemand(String demand) {
        return switch (normalize(demand)) {
            case "low" -> 0.35;
            case "high" -> 0.78;
            case "very_high" -> 0.88;
            default -> 0.60;
        };
    }

    private double companyTierMultiplier(String tier) {
        return switch (normalize(tier)) {
            case "tier_1", "faang", "top" -> 1.24;
            case "tier_2", "product" -> 1.10;
            case "tier_3", "service" -> 0.96;
            case "startup" -> 1.14;
            default -> 1.0;
        };
    }

    private double marketDemandMultiplier(String demand) {
        return switch (normalize(demand)) {
            case "low" -> 0.82;
            case "high" -> 1.16;
            case "very_high" -> 1.28;
            default -> 1.0;
        };
    }

    public record CalibratedAssumptions(
            double cityCostIndex,
            double interviewConversionRate,
            double historicalSalaryGrowth,
            double marketVolatility,
            double emergencyFundScore,
            double skillDemandScore,
            double companyTierMultiplier,
            double demandMultiplier
    ) {
    }
}
