package com.umesh.decision.service;

import com.umesh.decision.config.AppProperties;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class JiraIntegrationService {
    private final AppProperties appProperties;
    private final WebClient webClient;

    public JiraIntegrationService(AppProperties appProperties, WebClient webClient) {
        this.appProperties = appProperties;
        this.webClient = webClient;
    }

    public Map<String, Object> health() {
        var integrations = appProperties.integrations();
        boolean configured = integrations != null
                && StringUtils.hasText(integrations.jiraBaseUrl())
                && StringUtils.hasText(integrations.jiraEmail())
                && StringUtils.hasText(integrations.jiraApiToken());
        return Map.of("system", "jira", "configured", configured);
    }

    public String createDecisionIssue(String projectKey, String summary, String description) {
        var integrations = appProperties.integrations();
        if (integrations == null || !StringUtils.hasText(integrations.jiraBaseUrl())) {
            throw new IllegalStateException("JIRA integration is not configured.");
        }
        String token = Base64.getEncoder()
                .encodeToString((integrations.jiraEmail() + ":" + integrations.jiraApiToken()).getBytes());
        Map<String, Object> body = Map.of(
                "fields", Map.of(
                        "project", Map.of("key", projectKey),
                        "summary", summary,
                        "description", description,
                        "issuetype", Map.of("name", "Task")
                )
        );
        return webClient.post()
                .uri(integrations.jiraBaseUrl() + "/rest/api/2/issue")
                .header("Authorization", "Basic " + token)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
