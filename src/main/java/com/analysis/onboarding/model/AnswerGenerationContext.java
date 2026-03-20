package com.analysis.onboarding.model;

import com.analysis.onboarding.QuestionIntent;
import com.analysis.onboarding.model.EnvironmentReport;

import java.util.List;

public record AnswerGenerationContext(
        QuestionIntent intent,
        String question,
        String directAnswer,
        List<String> exactCommands,
        List<String> preconditions,
        List<String> orderedChecks,
        List<String> highLevelFlow,
        List<String> keyComponents,
        List<String> commonPitfalls,
        String currentFrameworkBehavior,
        String recommendedAdaptation,
        String caveats,
        String nextStep,
        List<String> suggestedFollowUps,
        EnvironmentReport environmentReport,
        List<ProjectChunk> retrievedChunks
) {
}
