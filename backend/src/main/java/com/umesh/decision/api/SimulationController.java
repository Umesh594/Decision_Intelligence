package com.umesh.decision.api;

import com.umesh.decision.api.dto.SimulationRequest;
import com.umesh.decision.api.dto.SimulationResponse;
import com.umesh.decision.service.SimulationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationResponse simulate(@Valid @RequestBody SimulationRequest request) {
        return simulationService.runSimulation(request);
    }

    @GetMapping("/templates/career-switch")
    public SimulationRequest careerSwitchTemplate() {
        return SimulationTemplates.careerSwitch();
    }

    @GetMapping("/history")
    public List<SimulationResponse> history() {
        return simulationService.history();
    }
}
