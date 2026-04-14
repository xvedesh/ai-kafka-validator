package com.analysis.failure;

import com.analysis.failure.model.CodeReference;
import com.analysis.failure.model.FailureAnalysis;
import com.analysis.failure.model.FailureEvidence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FailureAnalyzer {
    private final Path projectRoot;

    public FailureAnalyzer(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public FailureAnalysis analyze(FailureEvidence evidence) {
        FailureCategory category = classify(evidence);
        List<String> evidencePoints = buildEvidencePoints(evidence);
        List<CodeReference> codeReferences = buildCodeReferences(evidence, category);
        List<String> nextChecks = buildNextChecks(evidence, category);
        List<com.analysis.failure.model.FixProposal> fixProposals =
                new FixProposer().propose(category, evidence);

        return new FailureAnalysis(
                evidence,
                category,
                buildLikelyLayer(category),
                buildShortExplanation(evidence, category),
                buildWhatHappened(evidence),
                buildRootCause(evidence, category),
                evidencePoints,
                codeReferences,
                nextChecks,
                buildConfidence(category),
                fixProposals
        );
    }

    private FailureCategory classify(FailureEvidence evidence) {
        String combined = (evidence.getErrorMessage() + "\n" + evidence.getScenarioName() + "\n"
                + evidence.getFailedStepText()).toLowerCase(Locale.ROOT);

        if (containsAny(combined,
                "connection refused",
                "connectexception",
                "failed to connect",
                "server not started",
                "producer disconnected",
                "not authorized",
                "authentication failed")) {
            return FailureCategory.INFRASTRUCTURE_ERROR;
        }

        if (containsAny(combined,
                "no resolvable bootstrap urls",
                "unknownhostexception",
                "unknown host",
                "kafka consumer could not discover partitions",
                "kafkabootstrapservers",
                "baseuri")) {
            return FailureCategory.CONFIGURATION_ERROR;
        }

        if (containsAny(combined,
                "client not found for clientid",
                "account not found for accountid",
                "transaction clientid does not match account owner")) {
            return FailureCategory.RELATIONSHIP_VALIDATION_FAILURE;
        }

        if (containsAny(combined,
                "cannot delete client with existing",
                "cannot delete account with existing")) {
            return FailureCategory.DELETE_DEPENDENCY_FAILURE;
        }

        if (containsAny(combined,
                "timed out after",
                "waiting for kafka event")) {
            return FailureCategory.KAFKA_EVENT_NOT_FOUND;
        }

        if (containsAny(combined,
                "unexpected kafka event found",
                "no matching message consumed")) {
            return FailureCategory.TEST_DATA_ISSUE;
        }

        if (containsAny(combined,
                "kafka payload",
                "payload should match",
                "kafka topic mismatch",
                "kafka eventtype mismatch",
                "kafka entitytype mismatch",
                "kafka key should match",
                "kafka clientid mismatch",
                "kafka entityid mismatch")) {
            return FailureCategory.KAFKA_PAYLOAD_MISMATCH;
        }

        if (containsAny(combined,
                "unexpected http status code",
                "value mismatch for attribute",
                "expected error message not found in response body",
                "responsebody")) {
            return FailureCategory.API_ASSERTION_FAILURE;
        }

        if (containsAny(combined,
                "no api response is available",
                "no kafka event has been consumed",
                "kafka consumer has not been started",
                "no entity kafka event has been consumed",
                "no apiValidator found".toLowerCase(Locale.ROOT))) {
            return FailureCategory.TEST_DATA_ISSUE;
        }

        return FailureCategory.UNKNOWN;
    }

    private String buildLikelyLayer(FailureCategory category) {
        return switch (category) {
            case API_ASSERTION_FAILURE -> "API";
            case KAFKA_EVENT_NOT_FOUND, KAFKA_PAYLOAD_MISMATCH -> "Kafka";
            case RELATIONSHIP_VALIDATION_FAILURE, DELETE_DEPENDENCY_FAILURE -> "Server/Business Rules";
            case CONFIGURATION_ERROR -> "Configuration";
            case INFRASTRUCTURE_ERROR -> "Infrastructure";
            case TEST_DATA_ISSUE -> "Test Data/Scenario State";
            case UNKNOWN -> "Unknown";
        };
    }

    private String buildShortExplanation(FailureEvidence evidence, FailureCategory category) {
        return switch (category) {
            case API_ASSERTION_FAILURE ->
                    "The scenario failed during API response validation, so the returned HTTP result did not match the test expectation.";
            case KAFKA_EVENT_NOT_FOUND ->
                    "The API flow reached Kafka verification, but no matching event was found within the configured timeout.";
            case KAFKA_PAYLOAD_MISMATCH ->
                    "A Kafka message was consumed, but its metadata or payload did not match the expected API result.";
            case RELATIONSHIP_VALIDATION_FAILURE ->
                    "The backend rejected the request because the submitted entity relationship was invalid.";
            case DELETE_DEPENDENCY_FAILURE ->
                    "The delete operation was blocked by a dependency rule in the backend.";
            case CONFIGURATION_ERROR ->
                    "The failure pattern points to a configuration mismatch such as base URI, Kafka bootstrap servers, or topic wiring.";
            case INFRASTRUCTURE_ERROR ->
                    "The framework likely failed before business validation because a required runtime dependency was unreachable or unavailable.";
            case TEST_DATA_ISSUE ->
                    "The failure looks most consistent with scenario setup, stale runtime data, or an unexpected side effect that polluted the test state.";
            case UNKNOWN ->
                    "The failure could not be classified with high confidence from the available artifacts.";
        };
    }

    private String buildWhatHappened(FailureEvidence evidence) {
        String errorSnippet = firstMeaningfulLines(evidence.getErrorMessage(), 4);
        return "Scenario `" + evidence.getScenarioName() + "` failed on step `" + evidence.getFullFailedStep().trim()
                + "`. The failing step is mapped to `" + evidence.getStepDefinitionLocation() + "`."
                + (errorSnippet.isBlank() ? "" : " The captured failure text starts with: `" + sanitizeInline(errorSnippet) + "`.");
    }

    private String buildRootCause(FailureEvidence evidence, FailureCategory category) {
        String entity = detectEntity(evidence);
        return switch (category) {
            case API_ASSERTION_FAILURE ->
                    "The request completed, but the assertion layer did not see the expected HTTP outcome or payload shape. The most likely causes are an incorrect test expectation, a backend response contract change, or scenario state that was mutated differently than the API layer expected.";
            case KAFKA_EVENT_NOT_FOUND ->
                    "The Java test waited for a matching Kafka event by business key, but the event was not found. In this framework that usually means the backend route did not publish after a successful API action, the event went to a different topic, the consumer searched with the wrong entity id, or Kafka connectivity/topic discovery was not correct at runtime.";
            case KAFKA_PAYLOAD_MISMATCH ->
                    "Kafka validation reached a consumed message, so publishing and polling likely worked. The mismatch is more likely inside the event contract itself: wrong topic, wrong event type, wrong entity key, or a payload difference between the server response and the emitted Kafka message.";
            case RELATIONSHIP_VALIDATION_FAILURE ->
                    "The request was rejected by backend relationship validation. For this framework that usually means the " + entity + " request referenced a missing upstream entity or a cross-entity ownership rule was violated, and the backend correctly enforced the business rule.";
            case DELETE_DEPENDENCY_FAILURE ->
                    "The backend blocked the delete because dependent records still exist. That points to expected business protection logic rather than transport failure, and the next check is whether the scenario intended to prove that rule or expected the dependency data to be absent.";
            case CONFIGURATION_ERROR ->
                    "The evidence pattern looks like runtime configuration mismatch. The most likely places are `config.properties`, JVM `-D...` overrides, Docker environment variables, or Kafka topic/bootstrap settings passed to the mock server and Java test runner.";
            case INFRASTRUCTURE_ERROR ->
                    "The framework likely failed before meaningful application validation. The next thing to verify is whether the Node mock backend, Kafka broker, and Docker networking were actually available and reachable from the Java test process.";
            case TEST_DATA_ISSUE ->
                    "The failure suggests setup pollution or incorrect scenario state. In this framework that often means a stale `clients.json` runtime state, a missing prerequisite create step, an unexpected Kafka side effect from earlier data, or a step sequence that did not prepare the expected entity/thread-local context.";
            case UNKNOWN ->
                    "The available artifacts do not isolate one cause with high confidence. The strongest next move is to inspect the failing stack trace together with the feature step, matching step definition, and the most relevant backend/Kafka code path.";
        };
    }

    private List<String> buildEvidencePoints(FailureEvidence evidence) {
        List<String> points = new ArrayList<>();
        points.add("Feature: `" + evidence.getFeatureName() + "`");
        if (!evidence.getTags().isEmpty()) {
            points.add("Scenario tags: " + String.join(", ", evidence.getTags()));
        }
        if (evidence.getRerunReference() != null && !evidence.getRerunReference().isBlank()) {
            points.add("Rerun reference: `" + evidence.getRerunReference() + "`");
        }
        if (evidence.getSurefireSummary() != null && !evidence.getSurefireSummary().isBlank()) {
            points.add("Surefire summary: `" + sanitizeInline(firstMeaningfulLines(evidence.getSurefireSummary(), 2)) + "`");
        }
        if (evidence.getErrorMessage() != null && !evidence.getErrorMessage().isBlank()) {
            points.add("Stack trace / error snippet: ```text\n" + firstMeaningfulLines(evidence.getErrorMessage(), 10) + "\n```");
        } else {
            points.add("No explicit stack trace snippet was available inside `target/cucumber.json` for this failure.");
        }
        return points;
    }

    private List<CodeReference> buildCodeReferences(FailureEvidence evidence, FailureCategory category) {
        List<CodeReference> refs = new ArrayList<>();
        String entity = detectEntity(evidence);
        String action = detectAction(evidence);

        Path featurePath = resolveFeaturePath(evidence.getFeatureUri());
        refs.add(new CodeReference(
                "Failed feature step",
                relativize(featurePath),
                evidence.getFailedStepLine() > 0 ? evidence.getFailedStepLine() : null,
                evidence.getFullFailedStep().trim()
        ));

        if (!evidence.getStepDefinitionLocation().isBlank()) {
            addStepDefinitionReference(refs, evidence.getStepDefinitionLocation());
        }

        addApiReference(refs, entity, action);

        switch (category) {
            case KAFKA_EVENT_NOT_FOUND, KAFKA_PAYLOAD_MISMATCH -> {
                addKafkaReference(refs, entity, evidence);
                addServerRouteReference(refs, entity, action);
            }
            case RELATIONSHIP_VALIDATION_FAILURE -> {
                addRelationshipReference(refs, entity);
                addServerRouteReference(refs, entity, action);
            }
            case DELETE_DEPENDENCY_FAILURE -> {
                addDependencyReference(refs, entity);
                addServerRouteReference(refs, entity, "delete");
            }
            case CONFIGURATION_ERROR, INFRASTRUCTURE_ERROR -> addConfigReference(refs);
            case API_ASSERTION_FAILURE, TEST_DATA_ISSUE, UNKNOWN -> {
                addServerRouteReference(refs, entity, action);
                if (category == FailureCategory.TEST_DATA_ISSUE) {
                    refs.add(new CodeReference(
                            "Scenario context lifecycle",
                            relativize(projectRoot.resolve("src/test/java/com/kafka/ScenarioContext.java")),
                            findLine(projectRoot.resolve("src/test/java/com/kafka/ScenarioContext.java"), "public static void clear()"),
                            "Thread-local Kafka and consumed message cleanup"
                    ));
                }
            }
        }

        return refs;
    }

    private List<String> buildNextChecks(FailureEvidence evidence, FailureCategory category) {
        String entity = detectEntity(evidence);
        return switch (category) {
            case API_ASSERTION_FAILURE -> Arrays.asList(
                    "Compare the expected HTTP status/body in the feature step with the actual response attached in the report.",
                    "Inspect the `" + capitalize(entity) + "API` method used by the failing step and confirm which payload fields are generated before the assertion runs.",
                    "Check the matching route in `server/index.js` to confirm the backend response contract still matches the test expectation."
            );
            case KAFKA_EVENT_NOT_FOUND -> Arrays.asList(
                    "Check the mock server console for an `[AI-KAFKA-VALIDATOR][Kafka] Published ...` log line for the same entity id and event type.",
                    "Verify that the Java test and the Node server are using the same topic name and bootstrap server values.",
                    "Confirm the Kafka consumer searched with the expected business key from the API layer."
            );
            case KAFKA_PAYLOAD_MISMATCH -> Arrays.asList(
                    "Open the `Kafka Consumed Message` and `Kafka Payload Assertions` attachments in the report and compare them field by field.",
                    "Check whether the server route mutates the entity before publishing, especially on PUT/PATCH flows.",
                    "Inspect the Kafka validator class for the exact assertion that failed."
            );
            case RELATIONSHIP_VALIDATION_FAILURE -> Arrays.asList(
                    "Confirm whether the scenario was intended to be negative. If yes, verify the expected status/message still matches the backend rule.",
                    "Check the referenced upstream entity id values in the request body and ensure they exist in runtime data or were created earlier in the scenario.",
                    "Inspect `server/index.js` relationship validation for the affected entity."
            );
            case DELETE_DEPENDENCY_FAILURE -> Arrays.asList(
                    "Verify whether dependent records were intentionally created in the scenario or accidentally left behind in runtime data.",
                    "Check the dependency evaluation order in `server/index.js`, because the first matching dependency decides the response message.",
                    "If this should have been a successful delete, inspect the seed/reset state and prior scenario setup."
            );
            case CONFIGURATION_ERROR -> Arrays.asList(
                    "Verify `config.properties` and the `-D...` overrides used in the Maven command.",
                    "Check Docker and local environment variables for topic names, base URI, and Kafka bootstrap server alignment.",
                    "Run the health endpoint and confirm the mock service exposes the expected Kafka topics."
            );
            case INFRASTRUCTURE_ERROR -> Arrays.asList(
                    "Confirm the Node mock backend is running and reachable on the configured base URI.",
                    "Confirm Kafka is running and reachable from the Java process.",
                    "Review Docker container status and local port bindings before rerunning the scenario."
            );
            case TEST_DATA_ISSUE -> Arrays.asList(
                    "Reset runtime data and rerun the failing scenario in isolation.",
                    "Confirm prerequisite create steps executed before the failing assertion and that thread-local context was not cleared too early.",
                    "Check whether an earlier scenario published or removed data that changed the expected runtime state."
            );
            case UNKNOWN -> Arrays.asList(
                    "Open the failing scenario in the pretty report and inspect the exact step attachments.",
                    "Inspect the mapped step definition and adjacent API/Kafka code for the failing flow.",
                    "Rerun only the failing tag or scenario to narrow the evidence."
            );
        };
    }

    private String buildConfidence(FailureCategory category) {
        return switch (category) {
            case RELATIONSHIP_VALIDATION_FAILURE, DELETE_DEPENDENCY_FAILURE -> "HIGH";
            case API_ASSERTION_FAILURE, KAFKA_EVENT_NOT_FOUND, KAFKA_PAYLOAD_MISMATCH,
                    CONFIGURATION_ERROR, INFRASTRUCTURE_ERROR, TEST_DATA_ISSUE -> "MEDIUM";
            case UNKNOWN -> "LOW";
        };
    }

    private void addStepDefinitionReference(List<CodeReference> refs, String location) {
        int methodSeparator = location.lastIndexOf('.', location.indexOf('('));
        if (methodSeparator <= 0) {
            return;
        }

        String className = location.substring(0, methodSeparator);
        String methodName = location.substring(methodSeparator + 1, location.indexOf('('));
        Path file = projectRoot.resolve("src/test/java/" + className.replace('.', '/') + ".java");
        refs.add(new CodeReference(
                "Step definition",
                relativize(file),
                findLine(file, methodName + "("),
                methodName + "()"
        ));
    }

    private void addApiReference(List<CodeReference> refs, String entity, String action) {
        String apiClass = switch (entity) {
            case "account" -> "AccountAPI";
            case "portfolio" -> "PortfolioAPI";
            case "transaction" -> "TransactionAPI";
            default -> "ClientAPI";
        };
        Path file = projectRoot.resolve("src/test/java/com/api/" + apiClass + ".java");
        refs.add(new CodeReference(
                "API layer",
                relativize(file),
                findLine(file, action + "("),
                apiClass + "." + action + "()"
        ));
    }

    private void addKafkaReference(List<CodeReference> refs, String entity, FailureEvidence evidence) {
        boolean clientFlow = "client".equals(entity) || evidence.getFailedStepText().toLowerCase(Locale.ROOT).contains("client event");
        Path consumerFile = projectRoot.resolve(clientFlow
                ? "src/test/java/com/kafka/KafkaEventConsumer.java"
                : "src/test/java/com/kafka/EntityKafkaEventConsumer.java");
        String consumerMethod = evidence.getFailedStepText().toLowerCase(Locale.ROOT).contains("no kafka")
                ? (clientFlow ? "assertNoClientEvent" : "assertNoEntityEvent")
                : (clientFlow ? "waitForClientEvent" : "waitForEntityEvent");
        refs.add(new CodeReference(
                "Kafka consumer utility",
                relativize(consumerFile),
                findLine(consumerFile, consumerMethod + "("),
                consumerMethod + "()"
        ));

        Path validatorFile = projectRoot.resolve(clientFlow
                ? "src/test/java/com/kafka/KafkaEventValidator.java"
                : "src/test/java/com/kafka/EntityKafkaEventValidator.java");
        refs.add(new CodeReference(
                "Kafka validation utility",
                relativize(validatorFile),
                findLine(validatorFile, "validate"),
                "Kafka metadata/payload assertion helpers"
        ));

        Path publisherFile = projectRoot.resolve("server/kafkaPublisher.js");
        refs.add(new CodeReference(
                "Server Kafka publisher",
                relativize(publisherFile),
                findLine(publisherFile, clientFlow ? "publishClientEvent" : "publishEntityEvent"),
                "Kafka publish helper"
        ));
    }

    private void addServerRouteReference(List<CodeReference> refs, String entity, String action) {
        String routeBase = "/" + entity + "s";
        String routePattern = switch (action) {
            case "post" -> "server.post('" + routeBase + "'";
            case "put" -> "server.put('" + routeBase + "/:id'";
            case "patch" -> "server.patch('" + routeBase + "/:id'";
            case "delete" -> "server.delete('" + routeBase + "/:id'";
            default -> "server.post('" + routeBase + "'";
        };

        Path serverFile = projectRoot.resolve("server/index.js");
        refs.add(new CodeReference(
                "Mock backend route",
                relativize(serverFile),
                findLine(serverFile, routePattern),
                action.toUpperCase(Locale.ROOT) + " " + routeBase
        ));
    }

    private void addRelationshipReference(List<CodeReference> refs, String entity) {
        Path serverFile = projectRoot.resolve("server/index.js");
        String methodPattern = switch (entity) {
            case "account" -> "validateAccountRelationship";
            case "portfolio" -> "validatePortfolioRelationship";
            case "transaction" -> "validateTransactionRelationship";
            default -> "validateRelationship";
        };
        refs.add(new CodeReference(
                "Relationship validation logic",
                relativize(serverFile),
                findLine(serverFile, methodPattern),
                methodPattern + "()"
        ));
    }

    private void addDependencyReference(List<CodeReference> refs, String entity) {
        Path serverFile = projectRoot.resolve("server/index.js");
        String methodPattern = "client".equals(entity)
                ? "getDependencyViolationForClient"
                : "getDependencyViolationForAccount";
        refs.add(new CodeReference(
                "Delete dependency validation",
                relativize(serverFile),
                findLine(serverFile, methodPattern),
                methodPattern + "()"
        ));
    }

    private void addConfigReference(List<CodeReference> refs) {
        Path configFile = projectRoot.resolve("config.properties");
        refs.add(new CodeReference(
                "Java runtime configuration",
                relativize(configFile),
                findLine(configFile, "baseURI="),
                "baseURI and Kafka topic/bootstrap properties"
        ));

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        refs.add(new CodeReference(
                "Docker runtime wiring",
                relativize(dockerCompose),
                findLine(dockerCompose, "KAFKA_BOOTSTRAP_SERVERS"),
                "Environment variables passed to containers"
        ));
    }

    private String detectEntity(FailureEvidence evidence) {
        String combined = (evidence.getScenarioName() + " " + evidence.getFailedStepText()).toLowerCase(Locale.ROOT);
        if (combined.contains("transaction")) {
            return "transaction";
        }
        if (combined.contains("portfolio")) {
            return "portfolio";
        }
        if (combined.contains("account")) {
            return "account";
        }
        return "client";
    }

    private String detectAction(FailureEvidence evidence) {
        String combined = (evidence.getScenarioName() + " " + evidence.getFailedStepText()).toLowerCase(Locale.ROOT);
        if (combined.contains("patch")) {
            return "patch";
        }
        if (combined.contains("delete")) {
            return "delete";
        }
        if (combined.contains("update") || combined.contains(" via put") || combined.contains(" put ")) {
            return "put";
        }
        if (combined.contains("get")) {
            return "get";
        }
        return "post";
    }

    private Path resolveFeaturePath(String featureUri) {
        if (featureUri == null || featureUri.isBlank()) {
            return projectRoot.resolve("src/test/resources/features");
        }
        return projectRoot.resolve(featureUri);
    }

    private Integer findLine(Path path, String containsText) {
        if (path == null || containsText == null || !Files.exists(path)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                if (lines.get(lineIndex).contains(containsText)) {
                    return lineIndex + 1;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private String relativize(Path path) {
        if (path == null) {
            return "";
        }
        return projectRoot.relativize(path.toAbsolutePath()).toString();
    }

    private boolean containsAny(String source, String... candidates) {
        for (String candidate : candidates) {
            if (source.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstMeaningfulLines(String text, int maxLines) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] rawLines = text.split("\\R");
        StringBuilder builder = new StringBuilder();
        int added = 0;
        for (String rawLine : rawLines) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(line);
            added++;
            if (added == maxLines) {
                break;
            }
        }
        return builder.toString();
    }

    private String sanitizeInline(String value) {
        return value.replace(System.lineSeparator(), " | ");
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
