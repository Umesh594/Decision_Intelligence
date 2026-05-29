package com.umesh.decision.service;

import com.umesh.decision.config.AppProperties;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AzureDevOpsIntegrationService {
    private final AppProperties appProperties;

    public AzureDevOpsIntegrationService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Map<String, Object> health() {
        var integrations = appProperties.integrations();
        boolean configured = integrations != null
                && StringUtils.hasText(integrations.azureDevopsOrgUrl())
                && StringUtils.hasText(integrations.azureDevopsPat());
        return Map.of("system", "azure-devops", "configured", configured);
    }
}
