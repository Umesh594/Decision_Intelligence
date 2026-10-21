package com.umesh.decision.repository;

import com.umesh.decision.domain.SimulationCheckpoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationCheckpointRepository extends JpaRepository<SimulationCheckpoint, UUID> {
    Optional<SimulationCheckpoint> findByRunIdAndPathName(UUID runId, String pathName);

    List<SimulationCheckpoint> findByRunId(UUID runId);
}
