package com.umesh.decision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umesh.decision.api.dto.SimulationRequest;
import com.umesh.decision.api.dto.SimulationResponse;
import com.umesh.decision.config.AppProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

@Service
public class GroqInsightService {
    private static final Logger log = LoggerFactory.getLogger(GroqInsightService.class);

    private final AppProperties appProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GroqInsightService(AppProperties appProperties, WebClient webClient, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public String generateInsight(SimulationRequest request, SimulationResponse response) {
        if (appProperties.groq() == null || !StringUtils.hasText(appProperties.groq().apiKey())) {
            return fallbackInsight(response);
        }

        try {
            String prompt = """
                    You are an AI decision simulation analyst. Do not give personal advice.
                    Explain the probabilities, key tradeoffs, downside risks, target progress,
                    market calibration assumptions, and confidence signals.
                    Keep it concise, numeric, transparent, and business-friendly.

                    Scenario:
                    %s

                    Simulation:
                    %s
                    """.formatted(objectMapper.writeValueAsString(request), objectMapper.writeValueAsString(response.outcomes()));

            Map<String, Object> body = Map.of(
                    "model", appProperties.groq().model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", "You explain decision simulations with probabilities, not advice."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.2,
                    "max_tokens", 520
            );

            JsonNode json = webClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + appProperties.groq().apiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(700))
                            .filter(this::isRetryableGroqFailure))
                    .block();

            JsonNode text = json == null ? null : json.at("/choices/0/message/content");
            return text == null || text.isMissingNode() ? fallbackInsight(response) : text.asText();
        } catch (Exception exception) {
            log.warn("Groq insight generation failed; falling back to simulation-only insight: {}", exception.getMessage());
            return fallbackInsight(response);
        }
    }

    private String fallbackInsight(SimulationResponse response) {
        var best = response.outcomes().get(0);
        var second = response.outcomes().size() > 1 ? response.outcomes().get(1) : best;
        return "Simulation-only result: %s has the highest modeled goal probability at %.2f%% versus %.2f%% for %s. This is not advice; it reflects the configured assumptions, variance, downside risk, and target salary."
                .formatted(best.pathName(), best.goalProbability(), second.goalProbability(), second.pathName());
    }

    private boolean isRetryableGroqFailure(Throwable throwable) {
        if (throwable instanceof WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        }
        return false;
    }
}
