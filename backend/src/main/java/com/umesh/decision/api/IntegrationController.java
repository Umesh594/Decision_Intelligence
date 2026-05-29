package com.umesh.decision.api;

import com.umesh.decision.service.AzureDevOpsIntegrationService;
import com.umesh.decision.service.JiraIntegrationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
    private final JiraIntegrationService jiraIntegrationService;
    private final AzureDevOpsIntegrationService azureDevOpsIntegrationService;

    public IntegrationController(JiraIntegrationService jiraIntegrationService,
                                 AzureDevOpsIntegrationService azureDevOpsIntegrationService) {
        this.jiraIntegrationService = jiraIntegrationService;
        this.azureDevOpsIntegrationService = azureDevOpsIntegrationService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "jira", jiraIntegrationService.health(),
                "azureDevOps", azureDevOpsIntegrationService.health()
        );
    }
}
