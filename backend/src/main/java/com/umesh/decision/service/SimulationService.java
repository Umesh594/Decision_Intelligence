package com.umesh.decision.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umesh.decision.api.dto.DecisionPathRequest;
import com.umesh.decision.api.dto.PathOutcome;
import com.umesh.decision.api.dto.SimulationRequest;
import com.umesh.decision.api.dto.SimulationResponse;
import com.umesh.decision.domain.DecisionScenario;
import com.umesh.decision.domain.SimulationRun;
import com.umesh.decision.domain.SimulationStatus;
import com.umesh.decision.repository.DecisionScenarioRepository;
import com.umesh.decision.repository.SimulationRunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulationService {
    private final DecisionScenarioRepository scenarioRepository;
    private final SimulationRunRepository runRepository;
    private final GroqInsightService groqInsightService;
    private final CalibrationService calibrationService;
    private final ObjectMapper objectMapper;

    public SimulationService(DecisionScenarioRepository scenarioRepository, SimulationRunRepository runRepository,
                             GroqInsightService groqInsightService, CalibrationService calibrationService,
                             ObjectMapper objectMapper) {
        this.scenarioRepository = scenarioRepository;
        this.runRepository = runRepository;
        this.groqInsightService = groqInsightService;
        this.calibrationService = calibrationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SimulationResponse runSimulation(SimulationRequest request) {
        if (request.paths().size() < 2) {
            throw new IllegalArgumentException("At least two decision paths are required.");
        }

        DecisionScenario scenario = scenarioRepository.save(new DecisionScenario(
                UUID.randomUUID(),
                request.title(),
                request.location(),
                request.currentSalaryLpa(),
                request.offerSalaryLpa(),
                request.targetSalaryLpa(),
                request.years(),
                request.iterations(),
                request.riskTolerance(),
                OffsetDateTime.now()
        ));

        List<PathOutcome> outcomes = request.paths().stream()
                .map(path -> simulatePath(request, path))
                .sorted(Comparator.comparingDouble(PathOutcome::goalProbability).reversed())
                .toList();
        String winner = outcomes.get(0).pathName();
        SimulationResponse responseWithoutAi = new SimulationResponse(
                scenario.getId(),
                UUID.randomUUID(),
                request.title(),
                request.location(),
                request.years(),
                request.iterations(),
                outcomes,
                winner,
                "",
                OffsetDateTime.now()
        );
        String aiInsight = groqInsightService.generateInsight(request, responseWithoutAi);
        SimulationResponse response = new SimulationResponse(
                responseWithoutAi.scenarioId(),
                responseWithoutAi.runId(),
                responseWithoutAi.title(),
                responseWithoutAi.location(),
                responseWithoutAi.years(),
                responseWithoutAi.iterations(),
                responseWithoutAi.outcomes(),
                responseWithoutAi.winner(),
                aiInsight,
                responseWithoutAi.createdAt()
        );

        runRepository.save(new SimulationRun(
                response.runId(),
                scenario,
                SimulationStatus.COMPLETED,
                toJson(response),
                aiInsight,
                response.createdAt()
        ));
        return response;
    }

    @Transactional(readOnly = true)
    public List<SimulationResponse> history() {
        return runRepository.findAll().stream()
                .map(run -> fromJson(run.getSummaryJson()))
                .toList();
    }

    @Cacheable(value = "path-outcomes", key = "{#request.title(), #request.targetSalaryLpa(), #request.years(), #request.iterations(), #path.name()}")
    public PathOutcome simulatePath(SimulationRequest request, DecisionPathRequest path) {
        Random random = new Random(stableSeed(request, path));
        CalibrationService.CalibratedAssumptions calibration = calibrationService.calibrate(request.location(), request.calibration());
        List<Double> finalSalaries = new ArrayList<>(request.iterations());
        List<Double> wealthOutcomes = new ArrayList<>(request.iterations());
        double[] yearlyTotals = new double[request.years()];
        int goalHits = 0;
        int downsideEvents = 0;
        int recoveryHits = 0;

        for (int i = 0; i < request.iterations(); i++) {
            double salary = path.startingSalaryLpa();
            double accumulated = 0.0;
            boolean hadDownside = false;
            for (int year = 0; year < request.years(); year++) {
                double macroCycle = random.nextGaussian() * calibration.marketVolatility() * 0.18;
                double calibratedGrowth = path.annualGrowthMean() * 0.55
                        + calibration.historicalSalaryGrowth() * 0.35
                        + calibration.skillDemandScore() * 0.06
                        + (calibration.demandMultiplier() - 1) * 0.08;
                double growth = calibratedGrowth + macroCycle + random.nextGaussian() * (path.annualGrowthStdDev() + calibration.marketVolatility() * 0.18);
                salary *= Math.max(0.55, 1 + growth);
                double promotionChance = clamp(path.promotionProbability() * calibration.companyTierMultiplier() * (0.85 + calibration.skillDemandScore() * 0.35), 0, 0.92);
                if (random.nextDouble() < promotionChance) {
                    salary *= 1 + path.promotionSalaryBoost() * calibration.companyTierMultiplier();
                }
                double offerChance = clamp(path.switchProbability() * (0.55 + calibration.interviewConversionRate() * 2.4) * calibration.demandMultiplier(), 0, 0.94);
                if (random.nextDouble() < offerChance) {
                    salary *= 1 + path.switchSalaryBoost() * calibration.companyTierMultiplier() * (0.82 + calibration.skillDemandScore() * 0.35);
                }
                double layoffChance = clamp(path.layoffProbability() * (1.18 - calibration.emergencyFundScore() * 0.28) * (1 + calibration.marketVolatility() * 0.55), 0, 0.85);
                if (random.nextDouble() < layoffChance) {
                    salary *= 0.70 + calibration.emergencyFundScore() * 0.10;
                    downsideEvents++;
                    hadDownside = true;
                }
                if (hadDownside && salary >= path.startingSalaryLpa() * 1.12) {
                    recoveryHits++;
                    hadDownside = false;
                }
                salary *= 1 - (path.costOfLivingAdjustment() * calibration.cityCostIndex()) * 0.25;
                yearlyTotals[year] += salary;
                accumulated += salary;
            }
            if (salary >= request.targetSalaryLpa().doubleValue()) {
                goalHits++;
            }
            finalSalaries.add(salary);
            wealthOutcomes.add(accumulated);
        }

        finalSalaries.sort(Double::compareTo);
        wealthOutcomes.sort(Double::compareTo);
        DoubleSummaryStatistics salaryStats = finalSalaries.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        double mean = salaryStats.getAverage();
        double variance = finalSalaries.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0);
        double volatility = Math.sqrt(variance);
        double downsideRisk = downsideEvents * 100.0 / (request.iterations() * request.years());
        double resilience = downsideEvents == 0 ? 100.0 : recoveryHits * 100.0 / downsideEvents;
        double upside = Math.max(0, (percentile(finalSalaries, 0.90) - request.targetSalaryLpa().doubleValue()) * 100.0 / request.targetSalaryLpa().doubleValue());
        double riskPenalty = switch (request.riskTolerance()) {
            case LOW -> downsideRisk * 1.35 + volatility * 1.10;
            case MEDIUM -> downsideRisk + volatility * 0.85;
            case HIGH -> downsideRisk * 0.65 + volatility * 0.55;
        };
        double goalProbability = goalHits * 100.0 / request.iterations();
        double medianFinal = percentile(finalSalaries, 0.50);
        double targetProgress = clamp(medianFinal * 100.0 / request.targetSalaryLpa().doubleValue(), 0, 100);
        double riskAdjustedScore = clamp(
                goalProbability * 0.42
                        + targetProgress * 0.34
                        + resilience * 0.14
                        + upside * 0.10
                        - riskPenalty * 0.45,
                0,
                100
        );
        double confidenceScore = clamp(Math.sqrt(request.iterations()) / Math.sqrt(100000) * 100.0 - path.annualGrowthStdDev() * 40.0, 35, 96);
        List<Double> medianByYear = new ArrayList<>();
        for (double total : yearlyTotals) {
            medianByYear.add(round(total / request.iterations()));
        }

        return new PathOutcome(
                path.name(),
                round(goalProbability),
                round(medianFinal),
                round(percentile(finalSalaries, 0.10)),
                round(percentile(finalSalaries, 0.90)),
                round(wealthOutcomes.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                round(downsideRisk),
                round(volatility),
                round(riskAdjustedScore),
                round(targetProgress),
                round(resilience),
                round(upside),
                round(confidenceScore),
                medianByYear
        );
    }

    private long stableSeed(SimulationRequest request, DecisionPathRequest path) {
        return (request.title() + path.name() + request.years() + request.iterations()).hashCode();
    }

    private double percentile(List<Double> sorted, double percentile) {
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.round(percentile * (sorted.size() - 1))));
        return sorted.get(index);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String toJson(SimulationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize simulation response", e);
        }
    }

    private SimulationResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, SimulationResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to read simulation response", e);
        }
    }

}
