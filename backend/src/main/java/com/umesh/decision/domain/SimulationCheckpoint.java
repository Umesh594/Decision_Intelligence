package com.umesh.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Durable, per-(run, decision path) checkpoint for the concurrent Monte Carlo engine.
 *
 * <p>This is the persistence backbone of resumability: it records exactly how many batches of a
 * path's simulation have completed and the exact running aggregates accumulated so far (counts
 * and sums only — no derived statistics), plus a bounded reservoir sample used for percentile
 * estimation. On resume, the engine reloads this row, continues submitting batches starting at
 * {@code nextBatchIndex}, and merges new results additively into these same columns — so a crash
 * or retry can never re-run or double-count a batch that already completed. Deterministic
 * per-batch RNG seeding (see {@code com.umesh.decision.engine.SeedFactory}) is what makes resuming
 * at a specific batch index reproducible regardless of which worker thread executes it.
 */
@Entity
@Table(name = "simulation_checkpoints", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "path_name"}))
public class SimulationCheckpoint {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private SimulationRun run;

    @Column(name = "path_name", nullable = false, length = 160)
    private String pathName;

    @Column(name = "next_batch_index", nullable = false)
    private int nextBatchIndex;

    @Column(name = "completed_iterations", nullable = false)
    private long completedIterations;

    @Column(name = "goal_hits", nullable = false)
    private long goalHits;

    @Column(name = "downside_events", nullable = false)
    private long downsideEvents;

    @Column(name = "recovery_hits", nullable = false)
    private long recoveryHits;

    @Column(name = "sum_final_salary", nullable = false)
    private double sumFinalSalary;

    @Column(name = "sum_final_salary_sq", nullable = false)
    private double sumFinalSalarySq;

    @Column(name = "sum_wealth", nullable = false)
    private double sumWealth;

    @Column(name = "yearly_totals_json", nullable = false, columnDefinition = "TEXT")
    private String yearlyTotalsJson;

    @Column(name = "reservoir_json", nullable = false, columnDefinition = "TEXT")
    private String reservoirJson;

    @Column(name = "reservoir_seen_count", nullable = false)
    private long reservoirSeenCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SimulationCheckpoint() {
    }

    public SimulationCheckpoint(UUID id, SimulationRun run, String pathName, int nextBatchIndex,
                                long completedIterations, long goalHits, long downsideEvents, long recoveryHits,
                                double sumFinalSalary, double sumFinalSalarySq, double sumWealth,
                                String yearlyTotalsJson, String reservoirJson, long reservoirSeenCount,
                                OffsetDateTime updatedAt) {
        this.id = id;
        this.run = run;
        this.pathName = pathName;
        this.nextBatchIndex = nextBatchIndex;
        this.completedIterations = completedIterations;
        this.goalHits = goalHits;
        this.downsideEvents = downsideEvents;
        this.recoveryHits = recoveryHits;
        this.sumFinalSalary = sumFinalSalary;
        this.sumFinalSalarySq = sumFinalSalarySq;
        this.sumWealth = sumWealth;
        this.yearlyTotalsJson = yearlyTotalsJson;
        this.reservoirJson = reservoirJson;
        this.reservoirSeenCount = reservoirSeenCount;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public SimulationRun getRun() {
        return run;
    }

    public String getPathName() {
        return pathName;
    }

    public int getNextBatchIndex() {
        return nextBatchIndex;
    }

    public long getCompletedIterations() {
        return completedIterations;
    }

    public long getGoalHits() {
        return goalHits;
    }

    public long getDownsideEvents() {
        return downsideEvents;
    }

    public long getRecoveryHits() {
        return recoveryHits;
    }

    public double getSumFinalSalary() {
        return sumFinalSalary;
    }

    public double getSumFinalSalarySq() {
        return sumFinalSalarySq;
    }

    public double getSumWealth() {
        return sumWealth;
    }

    public String getYearlyTotalsJson() {
        return yearlyTotalsJson;
    }

    public String getReservoirJson() {
        return reservoirJson;
    }

    public long getReservoirSeenCount() {
        return reservoirSeenCount;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
