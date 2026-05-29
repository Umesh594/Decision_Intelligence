package com.umesh.decision.repository;

import com.umesh.decision.domain.DecisionScenario;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionScenarioRepository extends JpaRepository<DecisionScenario, UUID> {
}
