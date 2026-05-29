package com.umesh.decision.api;

import com.umesh.decision.api.dto.MarketCalibrationRequest;
import com.umesh.decision.api.dto.MarketIntelligenceResponse;
import com.umesh.decision.service.MarketIntelligenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market-intelligence")
public class MarketIntelligenceController {
    private final MarketIntelligenceService marketIntelligenceService;

    public MarketIntelligenceController(MarketIntelligenceService marketIntelligenceService) {
        this.marketIntelligenceService = marketIntelligenceService;
    }

    @PostMapping("/calibrate")
    public MarketIntelligenceResponse calibrate(@Valid @RequestBody MarketCalibrationRequest request) {
        return marketIntelligenceService.calibrate(request);
    }
}
