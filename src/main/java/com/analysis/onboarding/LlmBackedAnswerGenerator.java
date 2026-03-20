package com.analysis.onboarding;

import com.analysis.ai.OpenAiCompatibleClient;
import com.analysis.ai.OpenAiCompatibleConfig;
import com.analysis.ai.StructuredKnowledgeLayer;
import com.analysis.onboarding.model.AnswerGenerationContext;
import com.analysis.onboarding.model.EnvironmentCheck;
import com.analysis.onboarding.model.ProjectChunk;

import java.util.List;

public class LlmBackedAnswerGenerator implements OnboardingAnswerGenerator {
    private final OnboardingModelConfig config;
    private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();

    public LlmBackedAnswerGenerator(OnboardingModelConfig config) {
        this.config = config;
    }

    @Override
    public boolean isAvailable() {
        return config.isEnabled();
    }

    @Override
    public String modeDescription() {
        return config.modeDescription();
    }

    @Override
    public String generate(AnswerGenerationContext context) throws Exception {
        OpenAiCompatibleConfig requestConfig = new OpenAiCompatibleConfig(
                config.apiKey(),
                config.model(),
                config.baseUrl(),
                config.temperature(),
                config.modeDescription()
        );
        return client.generate(requestConfig, systemPrompt(), userPrompt(context));
    }

    private String systemPrompt() {
        return """
                You are the AI Kafka Validator setup and onboarding assistant.
                Behave like a strong SDET onboarding guide who understands REST APIs, Kafka validation, test automation, and troubleshooting.
                Use the structured framework knowledge and retrieved project evidence as the source of truth for framework-specific behavior.
                Answer the user's question first, then support the answer with practical steps and only a few references when useful.
                Do not act like a grep tool or file browser.
                For greetings or casual chat, answer briefly and naturally, then redirect to supported framework topics.
                For broad onboarding questions, give a useful high-level explanation instead of saying the question was not mapped.
                For setup and troubleshooting, keep the answer actionable and ordered.
                For enterprise adaptation questions, clearly separate the current framework from recommended future changes.
                Do not invent files, commands, features, or runtime behavior that are not supported by the provided context.
                Do not add a section named Relevant files because the caller may append compact references afterwards.
                """;
    }

    private String userPrompt(AnswerGenerationContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("Intent: ").append(context.intent()).append(System.lineSeparator());
        builder.append("Question: ").append(context.question()).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Structured knowledge primer:").append(System.lineSeparator());
        builder.append(StructuredKnowledgeLayer.onboardingPrimer(context.intent())).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Preferred structure:").append(System.lineSeparator());
        builder.append(structureHints(context.intent())).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Deterministic guidance:").append(System.lineSeparator());
        appendIfPresent(builder, "Direct answer", context.directAnswer());
        appendList(builder, "Exact commands", context.exactCommands());
        appendList(builder, "Preconditions", context.preconditions());
        appendList(builder, "Ordered checks", context.orderedChecks());
        appendList(builder, "High-level flow", context.highLevelFlow());
        appendList(builder, "Key components", context.keyComponents());
        appendList(builder, "Common pitfalls", context.commonPitfalls());
        appendIfPresent(builder, "Current framework behavior", context.currentFrameworkBehavior());
        appendIfPresent(builder, "Recommended adaptation", context.recommendedAdaptation());
        appendIfPresent(builder, "Caveats", context.caveats());
        appendIfPresent(builder, "Next step", context.nextStep());
        appendList(builder, "Suggested follow-ups", context.suggestedFollowUps());

        if (context.environmentReport() != null && !context.environmentReport().checks().isEmpty()) {
            builder.append(System.lineSeparator()).append("Environment checks:").append(System.lineSeparator());
            for (EnvironmentCheck check : context.environmentReport().checks()) {
                builder.append("- [")
                        .append(check.status())
                        .append("] ")
                        .append(check.name())
                        .append(": ")
                        .append(check.detail())
                        .append(System.lineSeparator());
            }
        }

        builder.append(System.lineSeparator()).append("Retrieved project context:").append(System.lineSeparator());
        List<ProjectChunk> chunks = context.retrievedChunks();
        if (chunks == null || chunks.isEmpty()) {
            builder.append("- No strongly matching project chunks were retrieved.");
        } else {
            for (int index = 0; index < Math.min(4, chunks.size()); index++) {
                ProjectChunk chunk = chunks.get(index);
                builder.append("[Source ").append(index + 1).append("] ")
                        .append(chunk.location()).append(" - ")
                        .append(chunk.title()).append(System.lineSeparator())
                        .append(trimChunk(chunk.content())).append(System.lineSeparator()).append(System.lineSeparator());
            }
        }

        builder.append("Write the answer in plain markdown. Answer the user like a helpful framework-aware assistant.");
        return builder.toString();
    }

    private String structureHints(QuestionIntent intent) {
        return switch (intent) {
            case SMALL_TALK -> """
                    1. Short natural response
                    2. One sentence about what you can help with
                    """;
            case FRAMEWORK_OVERVIEW -> """
                    1. Short explanation
                    2. High-level flow
                    3. Key components
                    4. Suggested follow-up topics
                    """;
            case SETUP_RUN, EXECUTION_HELP -> """
                    1. Direct answer
                    2. Explanation
                    3. Commands if needed
                    4. Common mistakes
                    5. Next step
                    """;
            case TROUBLESHOOTING -> """
                    1. Most likely issue
                    2. Ordered checks
                    3. Common causes
                    4. Suggested next action
                    """;
            case PROJECT_NAVIGATION -> """
                    1. Direct answer
                    2. Where to look
                    3. How the current framework uses it
                    4. Suggested follow-up topics
                    """;
            case KAFKA_EXPLANATION -> """
                    1. Short explanation
                    2. High-level flow
                    3. How the current framework does it
                    4. Suggested follow-up topics
                    """;
            case AGENT_EXPLANATION -> """
                    1. Direct answer
                    2. What each agent does
                    3. How to run it
                    4. Suggested follow-up topics
                    """;
            case ENTERPRISE_ADAPTATION -> """
                    1. Current framework behavior
                    2. What would change in a real system
                    3. Recommended adaptation path
                    4. Caveats
                    5. Safe next steps
                    """;
            case UNKNOWN_BUT_GENERAL -> """
                    1. Direct answer
                    2. High-level explanation
                    3. Suggested follow-up topics
                    """;
        };
    }

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append(": ").append(value).append(System.lineSeparator());
        }
    }

    private void appendList(StringBuilder builder, String label, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        builder.append(label).append(":").append(System.lineSeparator());
        for (String item : items) {
            builder.append("- ").append(item).append(System.lineSeparator());
        }
    }

    private String trimChunk(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() > 1200 ? normalized.substring(0, 1200) + "..." : normalized;
    }
}
