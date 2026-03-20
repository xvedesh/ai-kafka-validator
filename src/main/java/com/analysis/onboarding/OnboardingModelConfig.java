package com.analysis.onboarding;

import com.analysis.ai.OpenAiCompatibleConfig;

public record OnboardingModelConfig(
        String apiKey,
        String model,
        String baseUrl,
        double temperature,
        String modeDescription
) {
    public static OnboardingModelConfig fromEnvironment() {
        OpenAiCompatibleConfig config = OpenAiCompatibleConfig.forOnboarding();
        return new OnboardingModelConfig(
                config.apiKey(),
                config.model(),
                config.baseUrl(),
                config.temperature(),
                config.modeDescription()
        );
    }

    public boolean isEnabled() {
        return hasText(apiKey) && hasText(model);
    }

    public String chatCompletionsUrl() {
        return trimTrailingSlash(baseUrl) + "/chat/completions";
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
}
