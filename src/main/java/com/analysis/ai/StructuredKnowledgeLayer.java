package com.analysis.ai;

import com.analysis.onboarding.QuestionIntent;

public final class StructuredKnowledgeLayer {
    private StructuredKnowledgeLayer() {
    }

    public static String onboardingPrimer(QuestionIntent intent) {
        String commonPrimer = """
                Framework summary:
                - AI Kafka Validator is a Java 17 API and Kafka validation framework.
                - Runtime stack: Cucumber + TestNG + RestAssured + Node.js mock backend + Kafka + Docker.
                - Core domains: Client, Account, Portfolio, Transaction.
                - Runtime flow: Feature -> Step Definition -> Java API Layer -> Mock Server -> Kafka Publish -> Kafka Consumer -> Validation -> Reports.
                - The mock backend enforces relationship validation, delete dependency rules, and seed/reset behavior before Kafka publishing.
                - Full-stack Docker mode runs Kafka, mock API, and Java tests together.
                - Hybrid local mode runs Kafka in Docker and the server/tests on the host.
                - Reports are written under target/, and the Failure Analysis Agent reads those artifacts after execution.
                """;

        String intentPrimer = switch (intent) {
            case SMALL_TALK -> """
                    Conversation rule:
                    - Reply briefly and naturally.
                    - Do not retrieve or explain framework internals unless the user asks.
                    - Redirect gently toward supported setup, execution, Kafka, reporting, or troubleshooting topics.
                    """;
            case FRAMEWORK_OVERVIEW -> """
                    Overview focus:
                    - Explain what the framework validates.
                    - Explain the high-level runtime flow in plain English.
                    - Mention Docker/local execution, Kafka event validation, negative scenarios, and failure-analysis support when helpful.
                    """;
            case SETUP_RUN, EXECUTION_HELP -> """
                    Setup and execution focus:
                    - Prefer exact runnable commands.
                    - Clarify whether the answer applies to Docker, hybrid local mode, or both.
                    - Mention common misalignment points such as topic names, localhost vs docker hostnames, and missing prerequisites.
                    """;
            case TROUBLESHOOTING -> """
                    Troubleshooting focus:
                    - Lead with the most likely issue.
                    - Keep the check order practical: environment -> config -> runtime logs -> reports.
                    - Distinguish between Kafka, server, config, and data issues.
                    """;
            case PROJECT_NAVIGATION -> """
                    Navigation focus:
                    - Point to the most relevant files, not every possible file.
                    - Explain how those files fit together in the current framework.
                    """;
            case KAFKA_EXPLANATION -> """
                    Kafka focus:
                    - Successful API operations publish events from the Node backend.
                    - Java consumers validate by business key and event type, not by first message in the topic.
                    - Negative scenarios verify that failed operations do not emit matching Kafka events.
                    """;
            case AGENT_EXPLANATION -> """
                    Agent focus:
                    - Setup / Onboarding Agent helps users understand, run, troubleshoot, and extend the framework.
                    - Failure Analysis Agent explains failed runs using execution artifacts and framework context.
                    """;
            case ENTERPRISE_ADAPTATION -> """
                    Enterprise adaptation focus:
                    - Separate current implementation from recommended future adaptation.
                    - Do not claim current support for secure Kafka, real backend integration, CI pipelines, or database provisioning unless the repository explicitly shows it.
                    """;
            case UNKNOWN_BUT_GENERAL -> """
                    General guidance:
                    - Treat a broad question as an onboarding question first.
                    - Give a useful answer instead of saying the question was not mapped.
                    """;
        };

        return commonPrimer + System.lineSeparator() + intentPrimer;
    }

    public static String failurePrimer() {
        return """
                Failure-analysis knowledge:
                - The framework validates REST behavior and Kafka side effects together.
                - The Node mock backend in server/index.js enforces relationship rules and delete dependency protection before publishing Kafka events.
                - Kafka publishing happens through server/kafkaPublisher.js after successful operations.
                - Java Kafka consumers validate by business key and event type.
                - Negative scenarios expect backend rejection and no matching Kafka event.
                - Typical failure layers in this framework are: API assertions, Kafka event matching, server validation rules, runtime configuration, infrastructure availability, and test-data state.
                - Seed/reset relies on server/seed-data.json copied into server/clients.json.
                - Docker full-stack mode and hybrid local mode use different hostnames for Kafka, so config drift is a common cause of failures.
                """;
    }
}
