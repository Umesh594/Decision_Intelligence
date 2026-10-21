package com.umesh.decision.engine;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.SobolSequenceGenerator;

/**
 * Supplies the "systematic" (market-wide) risk factors for each iteration of a path's trajectory
 * using a scrambled 2-dimensional Sobol low-discrepancy sequence, instead of
 * {@code java.util.Random#nextGaussian()}.
 *
 * <p><b>Why quasi-random, and why only for these two factors:</b> within one simulated career
 * trajectory, year-over-year promotion/switch/layoff events and idiosyncratic growth noise are
 * genuinely meant to be i.i.d. across iterations — randomizing them is correct. But the two
 * dominant drivers of cross-iteration variance in the final outcome are (1) the year-0 macro
 * market shock and (2) a persistent, iteration-wide "luck" bias applied every year (systemic risk
 * factors, in the language of financial risk decomposition). Naive uniform random sampling of
 * exactly these two dominant dimensions leaves gaps and clumps across the iteration space, which
 * is what slows Monte Carlo convergence; a low-discrepancy sequence spreads them evenly, reducing
 * the variance of the sample mean for the same iteration count (the textbook justification for
 * quasi-Monte Carlo). A real Sobol generator (Apache Commons Math) is used rather than a
 * hand-rolled one to avoid the well-known pitfall of subtly broken direction numbers.
 *
 * <p><b>Decorrelation across batches/paths:</b> each batch owns its own
 * {@link SobolSequenceGenerator} instance (never shared across threads) and is assigned a
 * disjoint, deterministic slice of the global Sobol index space via {@link #skipTo}, so two
 * batches never draw the same quasi-random point. A per-run Cranley-Patterson rotation (an
 * additive scramble mod 1, derived from {@link SeedFactory#rotation}) is layered on top so that
 * different simulation runs/paths don't all see the exact same low-discrepancy point set, which
 * would otherwise make every run's "market luck" identical.
 */
public final class SystematicRiskSampler {
    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution(0, 1);
    private static final int DIMENSIONS = 2;

    private final SobolSequenceGenerator sobol = new SobolSequenceGenerator(DIMENSIONS);
    private final double[] rotation;

    public SystematicRiskSampler(double[] rotation, long globalStartIndex) {
        if (rotation.length != DIMENSIONS) {
            throw new IllegalArgumentException("Expected " + DIMENSIONS + " rotation components");
        }
        this.rotation = rotation;
        if (globalStartIndex > 0) {
            sobol.skipTo((int) globalStartIndex);
        }
    }

    /** Call once per iteration, in order, on this instance only (not thread-safe by design — one per batch task). */
    public Factors next() {
        double[] point = sobol.nextVector();
        double macroShockU = scramble(point[0], rotation[0]);
        double persistentBiasU = scramble(point[1], rotation[1]);
        double macroShockZ = STANDARD_NORMAL.inverseCumulativeProbability(clampOpenUnit(macroShockU));
        double persistentBiasZ = STANDARD_NORMAL.inverseCumulativeProbability(clampOpenUnit(persistentBiasU));
        return new Factors(macroShockZ, persistentBiasZ);
    }

    private double scramble(double value, double rotationOffset) {
        double shifted = value + rotationOffset;
        return shifted - Math.floor(shifted);
    }

    /** inverseCumulativeProbability is undefined at exactly 0 or 1; nudge into the open interval. */
    private double clampOpenUnit(double u) {
        double epsilon = 1e-12;
        return Math.min(1 - epsilon, Math.max(epsilon, u));
    }

    public record Factors(double macroShockZ, double persistentBiasZ) {
    }
}
