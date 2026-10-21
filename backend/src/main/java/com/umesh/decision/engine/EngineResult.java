package com.umesh.decision.engine;

/** Outcome of one {@link MonteCarloEngine#simulate} call: the accumulated aggregates plus run metadata. */
public record EngineResult(
        PathAccumulator accumulator,
        ConcurrentRunningStats.Snapshot goalProbabilityStats,
        int batchesCompleted,
        int totalBatches,
        boolean convergedEarly
) {
    public int nextBatchIndex() {
        return batchesCompleted;
    }

    public boolean isFullyComplete() {
        return batchesCompleted >= totalBatches || convergedEarly;
    }
}
