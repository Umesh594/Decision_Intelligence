package com.umesh.decision.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SimulationResponse(
        UUID scenarioId,
        UUID runId,
        String title,
        String location,
        int years,
        int iterations,
        List<PathOutcome> outcomes,
        String winner,
        String aiInsight,
        OffsetDateTime createdAt
) {
}
