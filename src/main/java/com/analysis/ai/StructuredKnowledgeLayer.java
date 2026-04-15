package com.analysis.ai;

public final class StructuredKnowledgeLayer {
    private StructuredKnowledgeLayer() {
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
