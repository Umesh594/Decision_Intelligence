package com.umesh.decision.service;

import com.umesh.decision.api.dto.CalibrationProfile;
import com.umesh.decision.config.AppProperties;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves the stochastic assumptions ("calibration") fed into the Monte Carlo engine for a
 * given location / role / company tier / market demand combination.
 *
 * <p><b>Deliberate simplification:</b> this is a static, hand-curated baseline dataset, not a
 * trained model and not backed by a live market-data feed. There is no external labor-market API
 * wired into this project. The dataset lives in {@code app.calibration.*} in application.yml (see
 * {@link AppProperties.Calibration}) rather than being embedded as Java constants, so it is
 * versionable and swappable per environment without a code change or redeploy of a new jar — but
 * it is still ultimately a reasoned baseline, not a learned model. Callers can override any
 * individual assumption per request via {@link CalibrationProfile}, which takes precedence over
 * the baseline. If config is absent (e.g. a plain unit test constructing this service directly),
 * a compiled-in fallback identical to the shipped application.yml defaults is used so the service
 * never silently produces nonsensical output.
 */
@Service
public class CalibrationService {
    private static final Map<String, Double> DEFAULT_CITY_COST_INDEX = Map.ofEntries(
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
    private static final Map<String, Double> DEFAULT_ROLE_LEVEL_CONVERSION = Map.of(
            "intern", 0.12, "fresher", 0.12, "junior", 0.18, "mid", 0.22, "senior", 0.16, "default", 0.18);
    private static final Map<String, Double> DEFAULT_ROLE_LEVEL_GROWTH = Map.of(
            "intern", 0.20, "fresher", 0.20, "junior", 0.16, "mid", 0.12, "senior", 0.09, "default", 0.13);
    private static final Map<String, Double> DEFAULT_MARKET_VOLATILITY = Map.of(
            "low", 0.10, "medium", 0.14, "high", 0.18, "very_high", 0.22, "default", 0.14);
    private static final Map<String, Double> DEFAULT_SKILL_DEMAND = Map.of(
            "low", 0.35, "medium", 0.60, "high", 0.78, "very_high", 0.88, "default", 0.60);
    private static final Map<String, Double> DEFAULT_TIER_MULTIPLIER = Map.ofEntries(
            Map.entry("tier_1", 1.24), Map.entry("faang", 1.24), Map.entry("top", 1.24),
            Map.entry("tier_2", 1.10), Map.entry("product", 1.10),
            Map.entry("tier_3", 0.96), Map.entry("service", 0.96),
            Map.entry("startup", 1.14), Map.entry("default", 1.0));
    private static final Map<String, Double> DEFAULT_DEMAND_MULTIPLIER = Map.of(
            "low", 0.82, "medium", 1.0, "high", 1.16, "very_high", 1.28, "default", 1.0);
    private static final double DEFAULT_CITY_COST = 1.0;
    private static final double DEFAULT_EMERGENCY_FUND_SCORE = 0.45;

    private final AppProperties.Calibration data;

    public CalibrationService() {
        this(null);
    }

    public CalibrationService(AppProperties appProperties) {
        this.data = appProperties == null ? null : appProperties.calibration();
    }

    public CalibratedAssumptions calibrate(String location, CalibrationProfile profile) {
        CalibrationProfile safe = profile == null
                ? new CalibrationProfile("Junior", "TIER_2", "MEDIUM", null, null, null, null, null, null)
                : profile;
        double cityCost = valueOrDefault(safe.cityCostIndex(), cityCostIndex(location));
        double conversion = valueOrDefault(safe.interviewConversionRate(), lookup(roleConversionTable(), safe.roleLevel()));
        double growth = valueOrDefault(safe.historicalSalaryGrowth(), lookup(roleGrowthTable(), safe.roleLevel()) * demandMultiplier(safe.marketDemand()));
        double volatility = valueOrDefault(safe.marketVolatility(), lookup(volatilityTable(), safe.marketDemand()));
        double emergencyFund = valueOrDefault(safe.emergencyFundScore(), emergencyFundDefault());
        double skillDemand = valueOrDefault(safe.skillDemandScore(), lookup(skillDemandTable(), safe.marketDemand()));
        double tierMultiplier = lookup(tierMultiplierTable(), safe.companyTier());
        double demandMultiplier = demandMultiplier(safe.marketDemand());
        return new CalibratedAssumptions(cityCost, conversion, growth, volatility, emergencyFund, skillDemand, tierMultiplier, demandMultiplier);
    }

    private double cityCostIndex(String location) {
        Map<String, Double> table = data != null && data.cityCostIndex() != null ? data.cityCostIndex() : DEFAULT_CITY_COST_INDEX;
        double fallback = data != null && data.defaultCityCostIndex() != null ? data.defaultCityCostIndex() : DEFAULT_CITY_COST;
        return table.getOrDefault(normalize(location), fallback);
    }

    private double emergencyFundDefault() {
        return data != null && data.defaultEmergencyFundScore() != null ? data.defaultEmergencyFundScore() : DEFAULT_EMERGENCY_FUND_SCORE;
    }

    private Map<String, Double> roleConversionTable() {
        return data != null && data.roleLevelInterviewConversion() != null ? data.roleLevelInterviewConversion() : DEFAULT_ROLE_LEVEL_CONVERSION;
    }

    private Map<String, Double> roleGrowthTable() {
        return data != null && data.roleLevelGrowth() != null ? data.roleLevelGrowth() : DEFAULT_ROLE_LEVEL_GROWTH;
    }

    private Map<String, Double> volatilityTable() {
        return data != null && data.marketDemandVolatility() != null ? data.marketDemandVolatility() : DEFAULT_MARKET_VOLATILITY;
    }

    private Map<String, Double> skillDemandTable() {
        return data != null && data.marketDemandSkillDemand() != null ? data.marketDemandSkillDemand() : DEFAULT_SKILL_DEMAND;
    }

    private Map<String, Double> tierMultiplierTable() {
        return data != null && data.companyTierMultiplier() != null ? data.companyTierMultiplier() : DEFAULT_TIER_MULTIPLIER;
    }

    private Map<String, Double> demandMultiplierTable() {
        return data != null && data.marketDemandMultiplier() != null ? data.marketDemandMultiplier() : DEFAULT_DEMAND_MULTIPLIER;
    }

    private double demandMultiplier(String demand) {
        return lookup(demandMultiplierTable(), demand);
    }

    private double lookup(Map<String, Double> table, String key) {
        String normalized = normalize(key);
        Double value = table.get(normalized.isEmpty() ? "default" : normalized);
        if (value != null) {
            return value;
        }
        return table.getOrDefault("default", 1.0);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private double valueOrDefault(Double value, double fallback) {
        return value == null ? fallback : value;
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
