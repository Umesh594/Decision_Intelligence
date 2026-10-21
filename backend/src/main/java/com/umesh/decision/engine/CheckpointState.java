package com.umesh.decision.engine;

/** Snapshot of a path's persisted progress, reloaded from {@code SimulationCheckpointRepository} to resume a run. */
public record CheckpointState(
        int nextBatchIndex,
        long completedIterations,
        long goalHits,
        long downsideEvents,
        long recoveryHits,
        double sumFinalSalary,
        double sumFinalSalarySq,
        double sumWealth,
        double[] yearlyTotals,
        double[] reservoirValues,
        long reservoirSeenCount
) {
    public PathAccumulator toAccumulator(int years, int reservoirCapacity) {
        return new PathAccumulator(years, reservoirCapacity, reservoirValues, reservoirSeenCount,
                completedIterations, goalHits, downsideEvents, recoveryHits,
                sumFinalSalary, sumFinalSalarySq, sumWealth, yearlyTotals);
    }
}
