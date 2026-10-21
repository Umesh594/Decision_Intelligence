package com.umesh.decision.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String apiKey,
        Groq groq,
        Integrations integrations,
        Simulation simulation,
        Calibration calibration
) {
    public record Groq(String apiKey, String model) {
    }

    public record Integrations(
            String jiraBaseUrl,
            String jiraEmail,
            String jiraApiToken,
            String azureDevopsOrgUrl,
            String azureDevopsPat
    ) {
    }

    /**
     * Tuning knobs for the concurrent Monte Carlo engine (see {@code com.umesh.decision.engine}).
     * All fields are nullable so the engine can fall back to sane defaults when a field (or the
     * whole {@code app.simulation} section) is omitted from configuration.
     */
    public record Simulation(
            Integer workerThreads,
            Integer executorQueueCapacity,
            Integer batchSize,
            Integer waveSize,
            Integer minBatchesBeforeConvergenceCheck,
            Double convergenceCiHalfWidthThresholdPct,
            Integer reservoirSize,
            Long batchTimeoutSeconds
    ) {
    }

    /**
     * Static baseline calibration dataset (city cost-of-living index, role/company/demand
     * multipliers). This is a deliberate simplification: the engine is not wired to a live
     * market-data feed, so these are reasonable, hand-curated India-tech-market baselines that a
     * caller can override per-request via {@link com.umesh.decision.api.dto.CalibrationProfile},
     * or operators can override wholesale by editing {@code app.calibration.*} in
     * application.yml (or an environment-specific profile) without recompiling or redeploying
     * code. Versioning this dataset (e.g. swapping in a new application.yml) is therefore a
     * config change, not a code change.
     */
    public record Calibration(
            Map<String, Double> cityCostIndex,
            Double defaultCityCostIndex,
            Map<String, Double> roleLevelInterviewConversion,
            Map<String, Double> roleLevelGrowth,
            Map<String, Double> marketDemandVolatility,
            Map<String, Double> marketDemandSkillDemand,
            Map<String, Double> companyTierMultiplier,
            Map<String, Double> marketDemandMultiplier,
            Double defaultEmergencyFundScore
    ) {
    }
}
