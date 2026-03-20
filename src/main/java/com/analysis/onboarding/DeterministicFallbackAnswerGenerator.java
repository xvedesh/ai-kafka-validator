package com.analysis.onboarding;

import com.analysis.onboarding.model.AnswerGenerationContext;
import com.analysis.onboarding.model.EnvironmentCheck;
import com.analysis.onboarding.model.EnvironmentReport;

import java.util.List;

public class DeterministicFallbackAnswerGenerator implements OnboardingAnswerGenerator {
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String modeDescription() {
        return "fallback (no OPENAI_API_KEY)";
    }

    @Override
    public String generate(AnswerGenerationContext context) {
        return switch (context.intent()) {
            case SMALL_TALK -> smallTalk(context);
            case FRAMEWORK_OVERVIEW -> frameworkOverview(context);
            case SETUP_RUN -> setupRun(context);
            case TROUBLESHOOTING -> troubleshooting(context);
            case PROJECT_NAVIGATION -> projectNavigation(context);
            case EXECUTION_HELP -> executionHelp(context);
            case KAFKA_EXPLANATION -> kafkaExplanation(context);
            case AGENT_EXPLANATION -> agentExplanation(context);
            case ENTERPRISE_ADAPTATION -> enterpriseAdaptation(context);
            case UNKNOWN_BUT_GENERAL -> unknownButGeneral(context);
        };
    }

    private String smallTalk(AnswerGenerationContext context) {
        return context.directAnswer();
    }

    private String frameworkOverview(AnswerGenerationContext context) {
        return joinSections(
                section("Short explanation", context.directAnswer()),
                section("High-level flow", ordered(context.highLevelFlow())),
                section("Key components", bullets(context.keyComponents())),
                section("Suggested follow-up topics", bullets(context.suggestedFollowUps()))
        );
    }

    private String setupRun(AnswerGenerationContext context) {
        return joinSections(
                section("Direct answer", context.directAnswer()),
                section("Explanation", context.currentFrameworkBehavior()),
                section("Commands", codeBlocks(context.exactCommands())),
                section("Preconditions", bullets(context.preconditions())),
                environmentSection(context.environmentReport()),
                section("Common mistakes", bullets(context.commonPitfalls())),
                section("Next step", context.nextStep())
        );
    }

    private String troubleshooting(AnswerGenerationContext context) {
        return joinSections(
                section("Most likely issue", context.directAnswer()),
                section("Ordered checks", ordered(context.orderedChecks())),
                environmentSection(context.environmentReport()),
                section("Common causes", bullets(context.commonPitfalls())),
                section("Suggested next action", context.nextStep())
        );
    }

    private String projectNavigation(AnswerGenerationContext context) {
        return joinSections(
                section("Direct answer", context.directAnswer()),
                section("Where to look", bullets(context.keyComponents())),
                section("How the current framework uses it", context.currentFrameworkBehavior()),
                section("Suggested follow-up topics", bullets(context.suggestedFollowUps()))
        );
    }

    private String executionHelp(AnswerGenerationContext context) {
        return joinSections(
                section("Direct answer", context.directAnswer()),
                section("Explanation", context.currentFrameworkBehavior()),
                section("Commands", codeBlocks(context.exactCommands())),
                section("Common mistakes", bullets(context.commonPitfalls())),
                section("Next step", context.nextStep())
        );
    }

    private String kafkaExplanation(AnswerGenerationContext context) {
        return joinSections(
                section("Direct answer", context.directAnswer()),
                section("High-level flow", ordered(context.highLevelFlow())),
                section("How the current framework does it", context.currentFrameworkBehavior()),
                section("Suggested follow-up topics", bullets(context.suggestedFollowUps()))
        );
    }

    private String agentExplanation(AnswerGenerationContext context) {
        return joinSections(
                section("Direct answer", context.directAnswer()),
                section("What each agent does", bullets(context.keyComponents())),
                section("How to run it", codeBlocks(context.exactCommands())),
                section("Suggested follow-up topics", bullets(context.suggestedFollowUps()))
        );
    }

    private String enterpriseAdaptation(AnswerGenerationContext context) {
        return joinSections(
                section("Current framework behavior", context.currentFrameworkBehavior()),
                section("What would change in a real system", context.recommendedAdaptation()),
                section("Recommended adaptation path", ordered(context.orderedChecks())),
                section("Caveats", context.caveats()),
                section("Safe next steps", context.nextStep())
        );
    }

    private String unknownButGeneral(AnswerGenerationContext context) {
        return joinSections(
                section("Direct answer", context.directAnswer()),
                section("High-level flow", ordered(context.highLevelFlow())),
                section("Good starting questions", bullets(context.suggestedFollowUps()))
        );
    }

    private String environmentSection(EnvironmentReport report) {
        if (report == null || report.checks().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (EnvironmentCheck check : report.checks()) {
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator());
            }
            builder.append("- [")
                    .append(check.status())
                    .append("] ")
                    .append(check.name())
                    .append(": ")
                    .append(check.detail());
        }
        return section("Environment checks", builder.toString());
    }

    private String ordered(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(index + 1).append(". ").append(items.get(index));
        }
        return builder.toString();
    }

    private String bullets(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append("- ").append(items.get(index));
        }
        return builder.toString();
    }

    private String codeBlocks(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < commands.size(); index++) {
            if (index > 0) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append("```bash")
                    .append(System.lineSeparator())
                    .append(commands.get(index).stripTrailing())
                    .append(System.lineSeparator())
                    .append("```");
        }
        return builder.toString();
    }

    private String section(String title, String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return title + System.lineSeparator() + content;
    }

    private String joinSections(String... sections) {
        StringBuilder builder = new StringBuilder();
        for (String section : sections) {
            if (section == null || section.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append(section);
        }
        return builder.toString();
    }
}
