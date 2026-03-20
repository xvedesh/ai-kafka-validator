package com.analysis.failure;

import com.analysis.ai.OpenAiCompatibleClient;
import com.analysis.ai.OpenAiCompatibleConfig;
import com.analysis.ai.StructuredKnowledgeLayer;
import com.analysis.failure.model.FailureAnalysis;
import com.analysis.onboarding.OnboardingKnowledgeBase;
import com.analysis.onboarding.RetrievalService;
import com.analysis.onboarding.model.ProjectChunk;

import java.nio.file.Path;
import java.util.List;

public class FailureLlmSummaryGenerator {
    private final OpenAiCompatibleConfig config = OpenAiCompatibleConfig.forFailureAnalysis();
    private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();
    private final RetrievalService retrievalService;

    public FailureLlmSummaryGenerator(Path projectRoot) {
        this.retrievalService = buildRetrievalService(projectRoot);
    }

    public boolean isAvailable() {
        return config.isEnabled();
    }

    public String tryGenerateSummary(List<FailureAnalysis> analyses, String mode) {
        if (!isAvailable() || analyses == null || analyses.isEmpty()) {
            return null;
        }

        try {
            return client.generate(config, systemPrompt(), userPrompt(analyses, mode));
        } catch (Exception exception) {
            return null;
        }
    }

    private RetrievalService buildRetrievalService(Path projectRoot) {
        try {
            return new RetrievalService(new OnboardingKnowledgeBase(projectRoot).load());
        } catch (Exception exception) {
            return new RetrievalService(List.of());
        }
    }

    private String systemPrompt() {
        return """
                You are the AI Kafka Validator failure-analysis assistant.
                Behave like a strong SDET performing root-cause analysis for API and Kafka automation failures.
                Use the structured framework knowledge and retrieved repository context as grounding.
                Summarize the failures in a technically useful way without inventing unsupported behavior.
                Focus on patterns, probable layers, and the most actionable fix directions.
                """;
    }

    private String userPrompt(List<FailureAnalysis> analyses, String mode) {
        StringBuilder builder = new StringBuilder();
        builder.append("Mode: ").append(mode).append(System.lineSeparator());
        builder.append("Failures analyzed: ").append(analyses.size()).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Structured framework primer:").append(System.lineSeparator());
        builder.append(StructuredKnowledgeLayer.failurePrimer()).append(System.lineSeparator()).append(System.lineSeparator());

        for (int index = 0; index < analyses.size(); index++) {
            FailureAnalysis analysis = analyses.get(index);
            builder.append(index + 1).append(". Failure record").append(System.lineSeparator());
            builder.append("Scenario: ").append(analysis.getEvidence().getScenarioName()).append(System.lineSeparator());
            builder.append("Failed step: ").append(analysis.getEvidence().getFullFailedStep().trim()).append(System.lineSeparator());
            builder.append("Category: ").append(analysis.getCategory()).append(System.lineSeparator());
            builder.append("Likely layer: ").append(analysis.getLikelyLayer()).append(System.lineSeparator());
            builder.append("What happened: ").append(analysis.getWhatHappened()).append(System.lineSeparator());
            builder.append("Why it happened: ").append(analysis.getRootCause()).append(System.lineSeparator());
            builder.append("Fix steps: ").append(String.join(" | ", analysis.getNextChecks())).append(System.lineSeparator());

            List<ProjectChunk> chunks = retrievalService.retrieve(buildQuery(analysis), 3);
            if (!chunks.isEmpty()) {
                builder.append("Retrieved repository context:").append(System.lineSeparator());
                for (ProjectChunk chunk : chunks) {
                    builder.append("- ").append(chunk.location()).append(" - ").append(chunk.title()).append(System.lineSeparator());
                    builder.append(trimChunk(chunk.content())).append(System.lineSeparator());
                }
            }
            builder.append(System.lineSeparator());
        }

        builder.append("Write a markdown section body only. Lead with the strongest patterns across the failures, then mention the most likely layers and best next actions.");
        return builder.toString();
    }

    private String buildQuery(FailureAnalysis analysis) {
        return analysis.getEvidence().getScenarioName() + " "
                + analysis.getEvidence().getFailedStepText() + " "
                + analysis.getCategory() + " "
                + analysis.getLikelyLayer() + " "
                + analysis.getEvidence().getErrorMessage();
    }

    private String trimChunk(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() > 900 ? normalized.substring(0, 900) + "..." : normalized;
    }
}
