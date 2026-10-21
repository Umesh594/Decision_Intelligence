package com.umesh.decision.engine;

/**
 * Deterministic, order-independent seed derivation for the concurrent Monte Carlo engine.
 *
 * <p>Every batch task gets a brand-new {@link java.util.SplittableRandom} constructed from a seed
 * that is a pure function of {@code (runFingerprint, pathName, batchIndex)}. This is deliberately
 * <b>not</b> implemented by repeatedly calling {@code SplittableRandom.split()} on a single shared
 * parent instance: {@code split()} mutates the parent's internal state, so sharing one parent
 * across worker threads would require external synchronization (recreating exactly the "shared
 * mutable Random" bug this engine exists to avoid), and the sequence of children it produces
 * depends on the order in which {@code split()} is called — which is exactly the kind of
 * scheduling-order dependence that breaks resumability (batch 7 must get the same seed whether it
 * runs on the first attempt or after a crash-resume, regardless of which thread picks it up or
 * in what order batches complete).
 *
 * <p>Instead, each batch's seed is computed independently via a SplitMix64-style finalizer mix of
 * the run fingerprint's hash, the path name's hash, and the batch index. This gives every batch an
 * independent, well-distributed 64-bit seed with zero shared state between threads and full
 * reproducibility across retries/resumes.
 */
public final class SeedFactory {
    private SeedFactory() {
    }

    public static long batchSeed(String runFingerprint, String pathName, int batchIndex) {
        long h = 0x9E3779B97F4A7C15L;
        h = mix(h ^ stableHash(runFingerprint));
        h = mix(h ^ stableHash(pathName));
        h = mix(h ^ (batchIndex * 0xBF58476D1CE4E5B9L));
        return h;
    }

    /** Deterministic per-run rotation offsets (Cranley-Patterson scrambling) for the quasi-random dimensions. */
    public static double[] rotation(String runFingerprint, String pathName, int dimensions) {
        double[] rotation = new double[dimensions];
        long h = mix(stableHash(runFingerprint) ^ stableHash(pathName) ^ 0xD6E8FEB86659FD93L);
        for (int i = 0; i < dimensions; i++) {
            h = mix(h + i);
            // Top 53 bits -> uniform double in [0, 1)
            rotation[i] = (h >>> 11) * 0x1.0p-53;
        }
        return rotation;
    }

    /** String.hashCode() is JVM-stable but only 32 bits and not well-mixed; widen and mix it. */
    private static long stableHash(String value) {
        long h = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            h = 31 * h + value.charAt(i);
        }
        return h;
    }

    /** SplitMix64 finalizer (Vigna / Steele, Lea, Flood) — a well-known, well-distributed bit mixer. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
