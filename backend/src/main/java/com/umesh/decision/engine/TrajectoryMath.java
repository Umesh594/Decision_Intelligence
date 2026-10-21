package com.umesh.decision.engine;

import com.umesh.decision.api.dto.DecisionPathRequest;
import com.umesh.decision.service.CalibrationService.CalibratedAssumptions;
import java.util.SplittableRandom;

/**
 * Pure, allocation-light simulation of a single career trajectory for one Monte Carlo iteration.
 *
 * <p>This is a deliberately side-effect-free function (no shared state, no I/O) so it can run
 * concurrently with zero coordination and be unit-tested directly without any engine/threading
 * machinery. Year 0's macro shock and every year's persistent bias come from the quasi-random
 * {@link SystematicRiskSampler} (the "systematic risk" factors); every other draw (promotion,
 * switch, layoff, idiosyncratic growth noise, and the macro shock in years &gt; 0) comes from the
 * batch's own {@link SplittableRandom}.
 */
public final class TrajectoryMath {
    private TrajectoryMath() {
    }

    public static IterationResult simulate(DecisionPathRequest path, int years, CalibratedAssumptions calibration,
                                           double targetSalaryLpa, SplittableRandom random,
                                           SystematicRiskSampler.Factors systematic) {
        double[] yearSalaries = new double[years];
        double salary = path.startingSalaryLpa();
        double accumulated = 0.0;
        boolean hadDownside = false;
        int downsideEvents = 0;
        int recoveryHits = 0;
        double systematicDrift = systematic.persistentBiasZ() * calibration.marketVolatility() * 0.05;

        for (int year = 0; year < years; year++) {
            double macroShock = (year == 0 ? systematic.macroShockZ() : random.nextGaussian())
                    * calibration.marketVolatility() * 0.18;
            double calibratedGrowth = path.annualGrowthMean() * 0.55
                    + calibration.historicalSalaryGrowth() * 0.35
                    + calibration.skillDemandScore() * 0.06
                    + (calibration.demandMultiplier() - 1) * 0.08;
            double growth = calibratedGrowth + systematicDrift + macroShock
                    + random.nextGaussian() * (path.annualGrowthStdDev() + calibration.marketVolatility() * 0.18);
            salary *= Math.max(0.55, 1 + growth);

            double promotionChance = clamp(path.promotionProbability() * calibration.companyTierMultiplier()
                    * (0.85 + calibration.skillDemandScore() * 0.35), 0, 0.92);
            if (random.nextDouble() < promotionChance) {
                salary *= 1 + path.promotionSalaryBoost() * calibration.companyTierMultiplier();
            }

            double offerChance = clamp(path.switchProbability() * (0.55 + calibration.interviewConversionRate() * 2.4)
                    * calibration.demandMultiplier(), 0, 0.94);
            if (random.nextDouble() < offerChance) {
                salary *= 1 + path.switchSalaryBoost() * calibration.companyTierMultiplier()
                        * (0.82 + calibration.skillDemandScore() * 0.35);
            }

            double layoffChance = clamp(path.layoffProbability() * (1.18 - calibration.emergencyFundScore() * 0.28)
                    * (1 + calibration.marketVolatility() * 0.55), 0, 0.85);
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
            yearSalaries[year] = salary;
            accumulated += salary;
        }

        boolean goalHit = salary >= targetSalaryLpa;
        return new IterationResult(salary, accumulated, goalHit, downsideEvents, recoveryHits, yearSalaries);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record IterationResult(double finalSalary, double accumulatedWealth, boolean goalHit,
                                  int downsideEvents, int recoveryHits, double[] yearSalaries) {
    }
}
