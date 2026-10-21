package com.umesh.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "simulation_runs")
public class SimulationRun {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private DecisionScenario scenario;

    @Enumerated(EnumType.STRING)
    private SimulationStatus status;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "ai_insight", columnDefinition = "TEXT")
    private String aiInsight;

    /**
     * Idempotency key uniquely identifying a logical simulation request: either a client-supplied
     * {@code Idempotency-Key} header, or (when absent) a SHA-256 content fingerprint of the
     * request. Retrying the same request resolves to the same run instead of re-running or
     * double-counting completed work.
     */
    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "target_iterations")
    private Integer targetIterations;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected SimulationRun() {
    }

    public SimulationRun(UUID id, DecisionScenario scenario, SimulationStatus status, String idempotencyKey,
                         Integer targetIterations, String summaryJson, String aiInsight,
                         OffsetDateTime createdAt, OffsetDateTime completedAt) {
        this.id = id;
        this.scenario = scenario;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.targetIterations = targetIterations;
        this.summaryJson = summaryJson;
        this.aiInsight = aiInsight;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public DecisionScenario getScenario() {
        return scenario;
    }

    public SimulationStatus getStatus() {
        return status;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public String getAiInsight() {
        return aiInsight;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Integer getTargetIterations() {
        return targetIterations;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
