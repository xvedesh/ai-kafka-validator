package com.analysis.onboarding;

public enum QuestionIntent {
    SMALL_TALK,
    FRAMEWORK_OVERVIEW,
    SETUP_RUN,
    TROUBLESHOOTING,
    PROJECT_NAVIGATION,
    EXECUTION_HELP,
    KAFKA_EXPLANATION,
    AGENT_EXPLANATION,
    ENTERPRISE_ADAPTATION,
    UNKNOWN_BUT_GENERAL;

    public static QuestionIntent classify(String question) {
        String normalized = question == null ? "" : question.trim().toLowerCase();
        if (normalized.isBlank()) {
            return UNKNOWN_BUT_GENERAL;
        }

        if (isSmallTalk(normalized)) {
            return SMALL_TALK;
        }
        if (containsAny(normalized,
                "what is this framework", "what is this project", "what is ai kafka validator",
                "what does this framework do", "how does this framework work", "how does it work",
                "framework overview", "give me an overview", "explain the framework", "understand the framework")) {
            return FRAMEWORK_OVERVIEW;
        }
        if (containsAny(normalized,
                "why can't", "why cant", "cannot run", "can't run", "not working", "unreachable",
                "no kafka events", "no kafka event", "kafka unreachable", "server not starting",
                "api not starting", "report empty", "reports empty", "why is the report empty",
                "reset-data not", "seed data not", "health check failing", "connection refused")) {
            return TROUBLESHOOTING;
        }
        if (containsAny(normalized,
                "how do i run it in docker", "run it in docker", "docker setup", "docker compose",
                "how do i run it locally", "run it locally", "local setup", "quick start", "how do i start",
                "start only kafka", "kafka only", "start kafka", "prerequisite", "prerequisites",
                "reset test data", "reset data", "seed data", "setup")) {
            return SETUP_RUN;
        }
        if (containsAny(normalized,
                "run only kafka", "run only negative", "negative scenarios", "negative tests",
                "kafka tests", "kafka scenarios", "transaction kafka", "account kafka", "portfolio kafka",
                "client kafka", "where are the reports", "where are reports", "report path", "report paths",
                "rerun failures", "rerun failed", "rerun failed scenarios", "fastest demo", "demo flow",
                "run only", "which tag")) {
            return EXECUTION_HELP;
        }
        if (containsAny(normalized,
                "which file", "where is", "where are", "navigate", "navigation", "project structure",
                "find the file", "where does", "where do", "which feature", "which features", "implemented")) {
            return PROJECT_NAVIGATION;
        }
        if (containsAny(normalized,
                "how does kafka", "kafka publishing", "kafka validation", "kafka flow",
                "publish account event", "publish transaction event", "account events", "portfolio events",
                "transaction events", "which file publishes")) {
            return KAFKA_EXPLANATION;
        }
        if (containsAny(normalized,
                "failure analysis agent", "what does the failure analysis agent do", "onboarding agent",
                "setup / onboarding agent", "setup agent", "what does the onboarding agent do")) {
            return AGENT_EXPLANATION;
        }
        if (containsAny(normalized,
                "real kafka", "kafka cluster", "multiple brokers", "schema registry", "sasl", "ssl",
                "real backend", "real api", "database", "db", "cloud", "ci/cd", "ci cd",
                "github actions", "jenkins", "azure devops", "enterprise", "external dependencies")) {
            return ENTERPRISE_ADAPTATION;
        }
        return UNKNOWN_BUT_GENERAL;
    }

    private static boolean isSmallTalk(String normalized) {
        return normalized.equals("hi")
                || normalized.equals("hello")
                || normalized.equals("hey")
                || normalized.equals("thanks")
                || normalized.equals("thank you")
                || normalized.equals("how are you")
                || normalized.equals("who are you")
                || normalized.equals("what can you do")
                || normalized.startsWith("hi ")
                || normalized.startsWith("hello ")
                || normalized.startsWith("hey ")
                || normalized.startsWith("how are you")
                || normalized.startsWith("thanks ")
                || normalized.startsWith("thank you ");
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
