package com.analysis.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String generate(OpenAiCompatibleConfig config, String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model());
        payload.put("temperature", config.temperature());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.chatCompletionsUrl()))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("LLM request failed with HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM response did not contain message content");
        }
        return content.trim();
    }
}
