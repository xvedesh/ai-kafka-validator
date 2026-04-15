package com.analysis.failure;

import com.analysis.failure.model.FailureAnalysis;
import com.analysis.failure.model.FailureEvidence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Builds the exact Maven command needed to rerun only the failing scenarios.
 *
 * Tag selection: prefers scenario-specific tags (e.g. @KafkaCreateClient) over
 * broad shared tags (e.g. @Kafka, @KafkaClientData).  When multiple failures
 * exist the tags are combined with the Cucumber {@code or} operator.
 *
 * Property values are sourced from {@code config.properties} so the suggested
 * command reflects the project's own defaults rather than hardcoded strings.
 */
public class RerunCommandBuilder {

    private static final Set<String> BROAD_TAGS = Set.of(
            "@kafka", "@negative", "@businessflow", "@kafkaclientdata",
            "@kafkaaccountdata", "@kafkaportfoliodata", "@kafkatransactiondata"
    );

    private final Path projectRoot;

    public RerunCommandBuilder(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public String build(List<FailureAnalysis> analyses) {
        if (analyses.isEmpty()) {
            return null;
        }

        Properties config = loadConfig();

        String baseUri = config.getProperty("baseURI", "http://localhost:3000");
        String kafkaBootstrap = config.getProperty("kafkaBootstrapServers", "localhost:9092");
        String clientTopic = config.getProperty("kafkaClientEventsTopic", "client-events");
        String accountTopic = config.getProperty("kafkaAccountEventsTopic", "account-events");
        String portfolioTopic = config.getProperty("kafkaPortfolioEventsTopic", "portfolio-events");
        String transactionTopic = config.getProperty("kafkaTransactionEventsTopic", "transaction-events");

        String tagExpression = buildTagExpression(analyses);

        StringBuilder sb = new StringBuilder();
        sb.append("sh ./mvnw test -Dtestng.suite.file=testng.xml \\\n");
        sb.append("  -DbaseURI=").append(baseUri).append(" \\\n");
        sb.append("  -DkafkaBootstrapServers=").append(kafkaBootstrap).append(" \\\n");
        sb.append("  -DkafkaClientEventsTopic=").append(clientTopic).append(" \\\n");
        sb.append("  -DkafkaAccountEventsTopic=").append(accountTopic).append(" \\\n");
        sb.append("  -DkafkaPortfolioEventsTopic=").append(portfolioTopic).append(" \\\n");
        sb.append("  -DkafkaTransactionEventsTopic=").append(transactionTopic).append(" \\\n");
        sb.append("  -Dcucumber.filter.tags=\"").append(tagExpression).append("\"");

        return sb.toString();
    }

    private String buildTagExpression(List<FailureAnalysis> analyses) {
        Set<String> specific = new LinkedHashSet<>();
        Set<String> fallback = new LinkedHashSet<>();

        for (FailureAnalysis analysis : analyses) {
            FailureEvidence evidence = analysis.getEvidence();
            List<String> tags = evidence.getTags();
            List<String> scenarioSpecific = scenarioSpecificTags(tags);
            if (!scenarioSpecific.isEmpty()) {
                specific.addAll(scenarioSpecific);
            } else {
                fallback.addAll(tags);
            }
        }

        Set<String> chosen = specific.isEmpty() ? fallback : specific;
        return String.join(" or ", chosen);
    }

    private List<String> scenarioSpecificTags(List<String> tags) {
        List<String> result = new ArrayList<>();
        for (String tag : tags) {
            if (!BROAD_TAGS.contains(tag.toLowerCase())) {
                result.add(tag);
            }
        }
        return result;
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        Path configFile = projectRoot.resolve("config.properties");
        if (Files.exists(configFile)) {
            try (InputStream is = Files.newInputStream(configFile)) {
                props.load(is);
            } catch (IOException ignored) {
            }
        }
        return props;
    }
}
