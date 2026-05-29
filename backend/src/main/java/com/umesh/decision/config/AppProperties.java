package com.umesh.decision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String apiKey,
        Groq groq,
        Integrations integrations
) {
    public record Groq(String apiKey, String model) {
    }

    public record Integrations(
            String jiraBaseUrl,
            String jiraEmail,
            String jiraApiToken,
            String azureDevopsOrgUrl,
            String azureDevopsPat
    ) {
    }
}
