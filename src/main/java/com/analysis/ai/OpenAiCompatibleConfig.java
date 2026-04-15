package com.analysis.ai;

import java.util.List;

public record OpenAiCompatibleConfig(
        String apiKey,
        String model,
        String baseUrl,
        double temperature,
        String modeLabel
) {
    public static OpenAiCompatibleConfig forOnboarding() {
        return fromNames(
                List.of("onboarding.llm.apiKey"),
                List.of("ONBOARDING_LLM_API_KEY", "OPENAI_API_KEY"),
                List.of("onboarding.llm.model"),
                List.of("ONBOARDING_LLM_MODEL", "OPENAI_MODEL"),
                List.of("onboarding.llm.baseUrl"),
                List.of("ONBOARDING_LLM_BASE_URL", "OPENAI_BASE_URL"),
                List.of("onboarding.llm.temperature"),
                List.of("ONBOARDING_LLM_TEMPERATURE", "OPENAI_TEMPERATURE"),
                "gpt-4.1-mini",
                "OpenAI"
        );
    }

    public static OpenAiCompatibleConfig forFailureAnalysis() {
        return fromNames(
                List.of("failure.llm.apiKey"),
                List.of("FAILURE_LLM_API_KEY", "OPENAI_API_KEY"),
                List.of("failure.llm.model"),
                List.of("FAILURE_LLM_MODEL", "OPENAI_MODEL"),
                List.of("failure.llm.baseUrl"),
                List.of("FAILURE_LLM_BASE_URL", "OPENAI_BASE_URL"),
                List.of("failure.llm.temperature"),
                List.of("FAILURE_LLM_TEMPERATURE", "OPENAI_TEMPERATURE"),
                "gpt-4.1-mini",
                "OpenAI"
        );
    }

    public boolean isEnabled() {
        return hasText(apiKey) && hasText(model);
    }

    public String modeDescription() {
        return isEnabled() ? "LLM-backed (OpenAI)" : "fallback (no OPENAI_API_KEY)";
    }

    public String chatCompletionsUrl() {
        return trimTrailingSlash(baseUrl) + "/chat/completions";
    }

    private static OpenAiCompatibleConfig fromNames(
            List<String> apiKeyProperties,
            List<String> apiKeyEnv,
            List<String> modelProperties,
            List<String> modelEnv,
            List<String> baseUrlProperties,
            List<String> baseUrlEnv,
            List<String> temperatureProperties,
            List<String> temperatureEnv,
            String defaultModel,
            String modeLabel
    ) {
        String apiKey = EnvValueResolver.firstNonBlank(apiKeyProperties, apiKeyEnv, "");
        String model = EnvValueResolver.firstNonBlank(modelProperties, modelEnv, defaultModel);
        String baseUrl = EnvValueResolver.firstNonBlank(baseUrlProperties, baseUrlEnv, "https://api.openai.com/v1");
        String temperatureRaw = EnvValueResolver.firstNonBlank(temperatureProperties, temperatureEnv, "0.2");
        return new OpenAiCompatibleConfig(apiKey, model, baseUrl, parseDouble(temperatureRaw), modeLabel);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimTrailingSlash(String value) {
        if (!hasText(value)) {
            return "https://api.openai.com/v1";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return 0.2d;
        }
    }
}
