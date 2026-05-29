package com.umesh.decision.service;

import com.umesh.decision.api.dto.CalibrationProfile;
import com.umesh.decision.api.dto.MarketCalibrationRequest;
import com.umesh.decision.api.dto.MarketIntelligenceResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarketIntelligenceService {
    private static final Map<String, SalaryBand> ROLE_BANDS = Map.ofEntries(
            Map.entry("software intern", new SalaryBand(1.8, 3.0, 5.0, 8.0, 0.20, 0.18)),
            Map.entry("software engineer", new SalaryBand(5.0, 9.0, 15.0, 26.0, 0.16, 0.15)),
            Map.entry("backend developer", new SalaryBand(6.0, 11.0, 18.0, 32.0, 0.17, 0.16)),
            Map.entry("java developer", new SalaryBand(5.5, 10.5, 18.0, 30.0, 0.15, 0.14)),
            Map.entry("spring boot developer", new SalaryBand(7.0, 13.0, 22.0, 38.0, 0.18, 0.16)),
            Map.entry("ai engineer", new SalaryBand(8.0, 16.0, 28.0, 48.0, 0.22, 0.20)),
            Map.entry("ml engineer", new SalaryBand(7.0, 15.0, 26.0, 45.0, 0.21, 0.20)),
            Map.entry("data engineer", new SalaryBand(7.0, 14.0, 24.0, 40.0, 0.18, 0.17))
    );

    private static final Map<String, Double> CITY_MULTIPLIER = Map.ofEntries(
            Map.entry("hyderabad", 1.05),
            Map.entry("bangalore", 1.18),
            Map.entry("bengaluru", 1.18),
            Map.entry("pune", 1.03),
            Map.entry("mumbai", 1.16),
            Map.entry("gurgaon", 1.14),
            Map.entry("delhi", 1.08),
            Map.entry("chennai", 0.98),
            Map.entry("nagpur", 0.82),
            Map.entry("remote", 0.95)
    );

    private static final Map<String, Double> SKILL_DEMAND = Map.ofEntries(
            Map.entry("java", 0.72),
            Map.entry("spring boot", 0.82),
            Map.entry("microservices", 0.78),
            Map.entry("kubernetes", 0.80),
            Map.entry("docker", 0.74),
            Map.entry("github actions", 0.68),
            Map.entry("jenkins", 0.62),
            Map.entry("ai", 0.86),
            Map.entry("rag", 0.84),
            Map.entry("llm", 0.88),
            Map.entry("postgresql", 0.70),
            Map.entry("redis", 0.68),
            Map.entry("system design", 0.83)
    );

    public MarketIntelligenceResponse calibrate(MarketCalibrationRequest request) {
        String roleKey = normalize(request.role());
        SalaryBand base = ROLE_BANDS.getOrDefault(roleKey, inferBand(roleKey));
        double cityMultiplier = CITY_MULTIPLIER.getOrDefault(normalize(request.location()), 1.0);
        double tierMultiplier = companyTierMultiplier(request.companyTier());
        double levelMultiplier = roleLevelMultiplier(request.roleLevel());
        double skillDemand = skillDemand(request.skills());
        double demandScore = clamp((base.demandScore() * 0.55 + skillDemand * 0.45) * tierMultiplier, 0.25, 0.98);
        double salaryMultiplier = cityMultiplier * tierMultiplier * levelMultiplier * (0.92 + demandScore * 0.16);

        double p25 = round(base.p25() * salaryMultiplier);
        double median = round(base.median() * salaryMultiplier);
        double p75 = round(base.p75() * salaryMultiplier);
        double p90 = round(base.p90() * salaryMultiplier);
        double conversion = round(clamp(0.08 + demandScore * 0.22 + skillDemand * 0.08, 0.06, 0.48));
        double growth = round(clamp(base.growthRate() * (0.80 + demandScore * 0.42), 0.04, 0.36));
        double volatility = round(clamp(base.volatility() * (1.24 - skillDemand * 0.28), 0.06, 0.30));
        double confidence = round(clamp(62 + knownRoleBonus(roleKey) + knownCityBonus(request.location()) + skillCoverageBonus(request.skills()), 35, 94));

        CalibrationProfile calibration = new CalibrationProfile(
                defaultText(request.roleLevel(), "Junior"),
                defaultText(request.companyTier(), "TIER_2"),
                demandLabel(demandScore),
                round(cityMultiplier),
                conversion,
                growth,
                volatility,
                0.45,
                round(skillDemand)
        );

        List<String> signals = new ArrayList<>();
        signals.add("Salary band adjusted by role, city, company tier and seniority.");
        signals.add("Interview conversion derived from market demand and skill strength.");
        signals.add("Historical growth and volatility calibrated from role family.");
        signals.add("Skill demand uses weighted signals from Java, Spring Boot, AI, DevOps and data stack keywords.");

        return new MarketIntelligenceResponse(
                request.role(),
                request.location(),
                calibration.roleLevel(),
                calibration.companyTier(),
                p25,
                median,
                p75,
                p90,
                round(demandScore),
                round(cityMultiplier),
                conversion,
                growth,
                volatility,
                round(skillDemand),
                confidence,
                calibration,
                signals,
                List.of("seed:india-tech-compensation-v1", "seed:city-cost-index-v1", "seed:skill-demand-v1", "external-provider-ready")
        );
    }

    private SalaryBand inferBand(String role) {
        if (role.contains("ai") || role.contains("machine") || role.contains("ml")) {
            return ROLE_BANDS.get("ai engineer");
        }
        if (role.contains("java") || role.contains("spring")) {
            return ROLE_BANDS.get("spring boot developer");
        }
        if (role.contains("backend")) {
            return ROLE_BANDS.get("backend developer");
        }
        if (role.contains("intern")) {
            return ROLE_BANDS.get("software intern");
        }
        return ROLE_BANDS.get("software engineer");
    }

    private double skillDemand(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return 0.58;
        }
        double total = 0;
        int matched = 0;
        for (String skill : skills) {
            String key = normalize(skill);
            double value = SKILL_DEMAND.getOrDefault(key, 0.56);
            total += value;
            matched++;
        }
        return clamp(total / Math.max(1, matched), 0.25, 0.98);
    }

    private double companyTierMultiplier(String tier) {
        return switch (normalize(tier)) {
            case "tier_1", "faang", "top" -> 1.32;
            case "tier_2", "product" -> 1.12;
            case "startup" -> 1.10;
            case "tier_3", "service" -> 0.92;
            default -> 1.0;
        };
    }

    private double roleLevelMultiplier(String level) {
        return switch (normalize(level)) {
            case "intern" -> 0.42;
            case "fresher" -> 0.62;
            case "junior" -> 0.86;
            case "mid" -> 1.18;
            case "senior" -> 1.62;
            default -> 1.0;
        };
    }

    private String demandLabel(double score) {
        if (score >= 0.82) return "VERY_HIGH";
        if (score >= 0.68) return "HIGH";
        if (score >= 0.48) return "MEDIUM";
        return "LOW";
    }

    private double knownRoleBonus(String role) {
        return ROLE_BANDS.containsKey(role) ? 10 : 3;
    }

    private double knownCityBonus(String location) {
        return CITY_MULTIPLIER.containsKey(normalize(location)) ? 8 : 2;
    }

    private double skillCoverageBonus(List<String> skills) {
        return Math.min(14, skills == null ? 0 : skills.size() * 2.2);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replace("-", " ");
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record SalaryBand(double p25, double median, double p75, double p90, double growthRate, double volatility) {
        double demandScore() {
            return clampStatic((growthRate * 3.2) + (p75 / 80.0), 0.35, 0.92);
        }

        private static double clampStatic(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
