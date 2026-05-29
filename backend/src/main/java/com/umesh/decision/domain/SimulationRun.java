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

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "ai_insight", columnDefinition = "TEXT")
    private String aiInsight;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected SimulationRun() {
    }

    public SimulationRun(UUID id, DecisionScenario scenario, SimulationStatus status,
                         String summaryJson, String aiInsight, OffsetDateTime createdAt) {
        this.id = id;
        this.scenario = scenario;
        this.status = status;
        this.summaryJson = summaryJson;
        this.aiInsight = aiInsight;
        this.createdAt = createdAt;
    }

    public String getSummaryJson() {
        return summaryJson;
    }
}
