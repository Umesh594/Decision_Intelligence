package com.umesh.decision.repository;

import com.umesh.decision.domain.SimulationRun;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {
}
