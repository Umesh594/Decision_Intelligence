package com.umesh.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "decision_scenarios")
public class DecisionScenario {
    @Id
    private UUID id;
    private String title;
    private String location;
    private BigDecimal currentSalaryLpa;
    private BigDecimal offerSalaryLpa;
    private BigDecimal targetSalaryLpa;
    private Integer years;
    private Integer iterations;
    @Enumerated(EnumType.STRING)
    private RiskTolerance riskTolerance;
    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected DecisionScenario() {
    }

    public DecisionScenario(UUID id, String title, String location, BigDecimal currentSalaryLpa,
                            BigDecimal offerSalaryLpa, BigDecimal targetSalaryLpa, Integer years,
                            Integer iterations, RiskTolerance riskTolerance, OffsetDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.location = location;
        this.currentSalaryLpa = currentSalaryLpa;
        this.offerSalaryLpa = offerSalaryLpa;
        this.targetSalaryLpa = targetSalaryLpa;
        this.years = years;
        this.iterations = iterations;
        this.riskTolerance = riskTolerance;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }
}
