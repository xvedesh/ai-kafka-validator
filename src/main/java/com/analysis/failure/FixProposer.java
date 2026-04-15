package com.analysis.failure;

import com.analysis.failure.model.FailureEvidence;
import com.analysis.failure.model.FixProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates concrete fix proposals for a given classified failure.
 *
 * For KAFKA_EVENT_NOT_FOUND the proposer extracts the expected event type from
 * the failing step and cross-references it against the event types the server
 * actually published (visible in the error message's inspected-records list).
 * When a mismatch is detected the exact feature file edit is proposed.
 */
public class FixProposer {

    private static final Pattern STEP_EVENT_TYPE_PATTERN =
            Pattern.compile("\"([A-Z0-9_]+)\"");

    private static final Pattern ERROR_OBSERVED_EVENT_TYPE_PATTERN =
            Pattern.compile("eventType=([A-Z0-9_]+)");

    private static final Pattern ERROR_EXPECTED_KEY_PATTERN =
            Pattern.compile("key='([^']+)'");

    public List<FixProposal> propose(FailureCategory category, FailureEvidence evidence) {
        return switch (category) {
            case KAFKA_EVENT_NOT_FOUND -> proposeKafkaEventNotFound(evidence);
            case CONFIGURATION_ERROR -> proposeConfigurationError(evidence);
            case INFRASTRUCTURE_ERROR -> proposeInfrastructureError();
            default -> List.of();
        };
    }

    private List<FixProposal> proposeKafkaEventNotFound(FailureEvidence evidence) {
        List<FixProposal> proposals = new ArrayList<>();

        String stepText = evidence.getFailedStepText();
        String errorMessage = evidence.getErrorMessage();

        String expectedEventType = extractEventTypeFromStep(stepText);
        if (expectedEventType == null || errorMessage == null) {
            return proposals;
        }

        // If the expected event type appears verbatim in inspected records the
        // event type is correct — the problem is elsewhere (topic, key, timeout).
        if (errorMessage.contains("eventType=" + expectedEventType)) {
            proposals.add(new FixProposal(
                    "The consumer polled the correct topic and found an event matching `" + expectedEventType
                            + "`, but the business key did not match. "
                            + "Verify that the entity ID used by the Kafka consumer matches the one returned by the API layer.",
                    null, null, null, null
            ));
            return proposals;
        }

        // No matching event type in the records — look for what the server actually published.
        String entityPrefix = entityPrefixOf(expectedEventType);
        String actionSuffix = actionSuffixOf(expectedEventType);
        String candidateEventType = findCandidateObservedEventType(
                errorMessage, entityPrefix, actionSuffix, expectedEventType);

        String featurePath = featureRelativePath(evidence.getFeatureUri());
        int failedLine = evidence.getFailedStepLine();

        if (candidateEventType != null && !candidateEventType.equals(expectedEventType)) {
            String currentText = "\"" + expectedEventType + "\"";
            String proposedText = "\"" + candidateEventType + "\"";
            proposals.add(new FixProposal(
                    "The step expects event type `" + expectedEventType
                            + "` but the server published `" + candidateEventType
                            + "`. Update the feature file step to use the correct event type.",
                    featurePath,
                    failedLine > 0 ? failedLine : null,
                    currentText,
                    proposedText
            ));
        } else if (hasInspectedRecords(errorMessage)) {
            // Records exist but no entity-matching event type found — topic or publisher mismatch.
            proposals.add(new FixProposal(
                    "The consumer polled the topic but found no event type matching the `"
                            + entityPrefix + "` entity. "
                            + "Verify that `server/kafkaPublisher.js` publishes an event of type `"
                            + expectedEventType + "` after the API call and that the topic name matches.",
                    null, null, null, null
            ));
        } else {
            // Empty inspected records — nothing was ever published.
            proposals.add(new FixProposal(
                    "No events were found on the topic at all. "
                            + "Ensure the mock server is running and Kafka is reachable before starting the test, "
                            + "and confirm the topic name in the test matches the topic the server publishes to.",
                    null, null, null, null
            ));
        }

        return proposals;
    }

    private List<FixProposal> proposeConfigurationError(FailureEvidence evidence) {
        String error = (evidence.getErrorMessage() + " " + evidence.getFailedStepText())
                .toLowerCase(Locale.ROOT);
        String detail;
        if (error.contains("baseuri") || error.contains("connection refused")) {
            detail = "Set `-DbaseURI=http://localhost:3000` (or the correct host) when running Maven, "
                    + "or update `baseURI` in `config.properties`.";
        } else {
            detail = "Set the correct `-DkafkaBootstrapServers=localhost:9092` and topic name overrides, "
                    + "or update the corresponding values in `config.properties`.";
        }
        return List.of(new FixProposal(
                "Configuration mismatch detected. " + detail,
                "config.properties", null, null, null
        ));
    }

    private List<FixProposal> proposeInfrastructureError() {
        return List.of(new FixProposal(
                "A required runtime dependency was unreachable. "
                        + "Start the mock server (`cd server && npm start`) and Kafka "
                        + "(`docker compose -f docker-compose.kafka.yml up -d`) "
                        + "from the project root, then rerun the scenario.",
                null, null, null, null
        ));
    }

    // --- helpers ---

    private String extractEventTypeFromStep(String stepText) {
        if (stepText == null) {
            return null;
        }
        Matcher m = STEP_EVENT_TYPE_PATTERN.matcher(stepText);
        return m.find() ? m.group(1) : null;
    }

    private String entityPrefixOf(String eventType) {
        int idx = eventType.indexOf('_');
        return idx > 0 ? eventType.substring(0, idx) : eventType;
    }

    private String actionSuffixOf(String eventType) {
        int idx = eventType.indexOf('_');
        return idx > 0 && idx < eventType.length() - 1 ? eventType.substring(idx + 1) : "";
    }

    /**
     * Scans the inspected-records section of the error message for an event type
     * that starts with the same entity prefix and matches the same action suffix.
     * Falls back to any event type sharing the entity prefix.
     */
    private String findCandidateObservedEventType(
            String errorMessage, String entityPrefix, String actionSuffix, String expectedEventType) {

        Matcher m = ERROR_OBSERVED_EVENT_TYPE_PATTERN.matcher(errorMessage);
        String fallback = null;
        while (m.find()) {
            String observed = m.group(1);
            if (observed.startsWith(entityPrefix + "_")) {
                // Prefer exact action suffix match (e.g., CREATED when expected is CLIENT_CREATED_V2)
                if (!observed.equals(expectedEventType)
                        && (actionSuffix.isEmpty() || observed.contains(primaryAction(actionSuffix)))) {
                    return observed;
                }
                if (fallback == null) {
                    fallback = observed;
                }
            }
        }
        return fallback;
    }

    /**
     * Returns the first word of an action suffix so that CLIENT_CREATED_V2 maps
     * back to the CREATED action and correctly matches CLIENT_CREATED.
     */
    private String primaryAction(String actionSuffix) {
        int idx = actionSuffix.indexOf('_');
        return idx > 0 ? actionSuffix.substring(0, idx) : actionSuffix;
    }

    private boolean hasInspectedRecords(String errorMessage) {
        return errorMessage != null
                && errorMessage.contains("Inspected records:")
                && !errorMessage.contains("Inspected records: []")
                && !errorMessage.contains("Inspected records: none");
    }

    private String featureRelativePath(String featureUri) {
        if (featureUri == null || featureUri.isBlank()) {
            return null;
        }
        // featureUri from Cucumber is typically an absolute file:// URI or a relative path
        String path = featureUri.replace("file:///", "").replace("file://", "");
        // Strip the project root prefix to get a relative path
        int srcIdx = path.indexOf("src/test/resources/features");
        return srcIdx >= 0 ? path.substring(srcIdx) : path;
    }
}
