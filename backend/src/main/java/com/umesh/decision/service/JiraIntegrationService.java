package com.umesh.decision.service;

import com.umesh.decision.config.AppProperties;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Reports whether a JIRA integration is configured. The project does not create or sync JIRA
 * issues from a simulation (there is no product requirement driving that yet); this is
 * deliberately limited to a configuration health check rather than carrying a half-built,
 * never-invoked issue-creation code path.
 */
@Service
public class JiraIntegrationService {
    private final AppProperties appProperties;

    public JiraIntegrationService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Map<String, Object> health() {
        var integrations = appProperties.integrations();
        boolean configured = integrations != null
                && StringUtils.hasText(integrations.jiraBaseUrl())
                && StringUtils.hasText(integrations.jiraEmail())
                && StringUtils.hasText(integrations.jiraApiToken());
        return Map.of("system", "jira", "configured", configured);
    }
}
