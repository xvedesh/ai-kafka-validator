package com.analysis.onboarding;

import com.analysis.onboarding.model.AnswerGenerationContext;
import com.analysis.onboarding.model.EnvironmentReport;
import com.analysis.onboarding.model.ProjectChunk;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SetupOnboardingService {
    private static final String FULL_SUITE_COMMAND = "sh ./mvnw clean test -Dtestng.suite.file=testng.xml -DbaseURI=http://localhost:3000 -DkafkaBootstrapServers=localhost:9092 -DkafkaClientEventsTopic=client-events -DkafkaAccountEventsTopic=account-events -DkafkaPortfolioEventsTopic=portfolio-events -DkafkaTransactionEventsTopic=transaction-events";
    private static final String DOCKER_FULL_STACK_COMMAND = "docker compose up --build --abort-on-container-exit --exit-code-from api-tests";
    private static final String START_KAFKA_ONLY_COMMAND = "docker compose -f docker-compose.kafka.yml up -d";
    private static final String SERVER_START_COMMAND = """
            cd server
            npm install
            KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \\
            KAFKA_CLIENT_EVENTS_TOPIC=client-events \\
            KAFKA_ACCOUNT_EVENTS_TOPIC=account-events \\
            KAFKA_PORTFOLIO_EVENTS_TOPIC=portfolio-events \\
            KAFKA_TRANSACTION_EVENTS_TOPIC=transaction-events \\
            npm start
            """;
    private static final String NEGATIVE_RUN_COMMAND = "sh ./mvnw clean test -Dtestng.suite.file=testng.xml -Dcucumber.filter.tags=\"@Negative\" -DbaseURI=http://localhost:3000 -DkafkaBootstrapServers=localhost:9092 -DkafkaClientEventsTopic=client-events -DkafkaAccountEventsTopic=account-events -DkafkaPortfolioEventsTopic=portfolio-events -DkafkaTransactionEventsTopic=transaction-events";
    private static final String ALL_KAFKA_RUN_COMMAND = "sh ./mvnw clean test -Dtestng.suite.file=testng.xml -Dcucumber.filter.tags=\"@KafkaClientData or @KafkaAccountData or @KafkaPortfolioData or @KafkaTransactionData\" -DbaseURI=http://localhost:3000 -DkafkaBootstrapServers=localhost:9092 -DkafkaClientEventsTopic=client-events -DkafkaAccountEventsTopic=account-events -DkafkaPortfolioEventsTopic=portfolio-events -DkafkaTransactionEventsTopic=transaction-events";
    private static final String TRANSACTION_KAFKA_RUN_COMMAND = "sh ./mvnw clean test -Dtestng.suite.file=testng.xml -Dcucumber.filter.tags=\"@KafkaTransactionData\" -DbaseURI=http://localhost:3000 -DkafkaBootstrapServers=localhost:9092 -DkafkaClientEventsTopic=client-events -DkafkaAccountEventsTopic=account-events -DkafkaPortfolioEventsTopic=portfolio-events -DkafkaTransactionEventsTopic=transaction-events";
    private static final String RERUN_COMMAND = "sh ./mvnw test -Dtestng.suite.file=testng-rerun.xml -DbaseURI=http://localhost:3000 -DkafkaBootstrapServers=localhost:9092 -DkafkaClientEventsTopic=client-events -DkafkaAccountEventsTopic=account-events -DkafkaPortfolioEventsTopic=portfolio-events -DkafkaTransactionEventsTopic=transaction-events";
    private static final String FAILURE_AGENT_COMMAND = "sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.failure.FailureAnalysisAgent";
    private static final String ONBOARDING_AGENT_COMMAND = "sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.onboarding.SetupOnboardingAgent";

    private final Path projectRoot;
    private final RetrievalService retrievalService;
    private final EnvironmentCheckService environmentCheckService;
    private final OnboardingAnswerGenerator llmGenerator;
    private final OnboardingAnswerGenerator fallbackGenerator;

    public SetupOnboardingService(Path projectRoot, List<ProjectChunk> chunks) {
        this.projectRoot = projectRoot;
        this.retrievalService = new RetrievalService(chunks);
        this.environmentCheckService = new EnvironmentCheckService();
        this.llmGenerator = new LlmBackedAnswerGenerator(OnboardingModelConfig.fromEnvironment());
        this.fallbackGenerator = new DeterministicFallbackAnswerGenerator();
    }

    public String modeDescription() {
        return llmGenerator.isAvailable() ? llmGenerator.modeDescription() : fallbackGenerator.modeDescription();
    }

    public String answer(String question) {
        String normalizedQuestion = question == null ? "" : question.trim();
        QuestionIntent intent = QuestionIntent.classify(normalizedQuestion);
        List<ProjectChunk> retrievedChunks = shouldRetrieve(intent)
                ? retrievalService.retrieve(buildRetrievalQuery(intent, normalizedQuestion), 6)
                : List.of();
        EnvironmentReport environmentReport = needsEnvironmentChecks(intent)
                ? environmentCheckService.runBasicChecks(projectRoot)
                : null;

        AnswerGenerationContext context = buildContext(intent, normalizedQuestion, retrievedChunks, environmentReport);
        String answer = generateAnswer(context).trim();
        String references = formatRelevantFiles(intent, retrievedChunks);

        if (references.isBlank()) {
            return answer;
        }
        return answer + System.lineSeparator() + System.lineSeparator() + references;
    }

    private String generateAnswer(AnswerGenerationContext context) {
        if (context.intent() == QuestionIntent.SMALL_TALK) {
            return safeFallback(context);
        }
        if (llmGenerator.isAvailable()) {
            try {
                return llmGenerator.generate(context);
            } catch (Exception exception) {
                return safeFallback(context)
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "Mode note"
                        + System.lineSeparator()
                        + "LLM mode was configured but unavailable for this answer, so I used the local grounded fallback instead.";
            }
        }
        return safeFallback(context);
    }

    private String safeFallback(AnswerGenerationContext context) {
        try {
            return fallbackGenerator.generate(context);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate onboarding answer in fallback mode", exception);
        }
    }

    private boolean shouldRetrieve(QuestionIntent intent) {
        return intent != QuestionIntent.SMALL_TALK;
    }

    private boolean needsEnvironmentChecks(QuestionIntent intent) {
        return intent == QuestionIntent.SETUP_RUN || intent == QuestionIntent.TROUBLESHOOTING;
    }

    private String buildRetrievalQuery(QuestionIntent intent, String question) {
        return switch (intent) {
            case SMALL_TALK -> question;
            case FRAMEWORK_OVERVIEW -> question + " architecture runtime flow cucumber testng restassured kafka docker node mock backend negative scenarios cross entity";
            case SETUP_RUN -> question + " quick start prerequisites docker local setup kafka only server start reset data seed data commands";
            case TROUBLESHOOTING -> question + " troubleshooting kafka localhost 9092 health docker server reports rerun reset data config properties";
            case PROJECT_NAVIGATION -> question + " where implemented file location feature step definition api class kafka utility server failure agent";
            case EXECUTION_HELP -> question + " tags negative kafka transaction reports rerun demo flow execution commands";
            case KAFKA_EXPLANATION -> question + " kafka publishing kafka validation topic event producer consumer entity client payload";
            case AGENT_EXPLANATION -> question + " failure analysis agent onboarding agent artifacts reports docs commands";
            case ENTERPRISE_ADAPTATION -> question + " enterprise ci cd github actions jenkins azure devops real kafka cluster database backend cloud config";
            case UNKNOWN_BUT_GENERAL -> question + " framework overview setup docker local kafka reports architecture";
        };
    }

    private AnswerGenerationContext buildContext(QuestionIntent intent, String question, List<ProjectChunk> chunks,
                                                 EnvironmentReport environmentReport) {
        return switch (intent) {
            case SMALL_TALK -> smallTalkContext(question);
            case FRAMEWORK_OVERVIEW -> frameworkOverviewContext(question, chunks);
            case SETUP_RUN -> setupRunContext(question, chunks, environmentReport);
            case TROUBLESHOOTING -> troubleshootingContext(question, chunks, environmentReport);
            case PROJECT_NAVIGATION -> projectNavigationContext(question, chunks);
            case EXECUTION_HELP -> executionHelpContext(question, chunks);
            case KAFKA_EXPLANATION -> kafkaExplanationContext(question, chunks);
            case AGENT_EXPLANATION -> agentExplanationContext(question, chunks);
            case ENTERPRISE_ADAPTATION -> enterpriseAdaptationContext(question, chunks);
            case UNKNOWN_BUT_GENERAL -> unknownGeneralContext(question, chunks);
        };
    }

    private AnswerGenerationContext smallTalkContext(String question) {
        String directAnswer = question.toLowerCase(Locale.ROOT).contains("how are you")
                ? "Doing well, thank you - hope you are too. How can I help you with AI Kafka Validator today?"
                : "Hello. How can I help you with AI Kafka Validator today?";

        return new AnswerGenerationContext(
                QuestionIntent.SMALL_TALK,
                question,
                directAnswer,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                "",
                "",
                "Ask about Docker, local setup, Kafka validation, reports, troubleshooting, or framework structure.",
                List.of(
                        "How does this framework work?",
                        "How do I run it in Docker?",
                        "How do I run only negative scenarios?",
                        "Why can't I run Kafka tests locally?"
                ),
                null,
                List.of()
        );
    }

    private AnswerGenerationContext frameworkOverviewContext(String question, List<ProjectChunk> chunks) {
        return new AnswerGenerationContext(
                QuestionIntent.FRAMEWORK_OVERVIEW,
                question,
                "AI Kafka Validator is a Java 17 test automation framework that validates both REST API responses and Kafka event side effects. It combines Cucumber + TestNG, RestAssured, a Node.js mock backend, Kafka validation, negative business scenarios, chained cross-entity flows, and local failure-analysis tooling in one runnable project.",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "Cucumber feature files drive the test scenarios.",
                        "Step definitions call entity-specific Java API classes built on RestAssured.",
                        "Requests hit the Node.js mock backend, which applies business relationship and delete-dependency validation.",
                        "Successful operations publish Kafka events through the server-side publisher.",
                        "Java Kafka consumers wait for matching events by business key and validate event metadata and payload.",
                        "Reports and optional failure-analysis artifacts are written under `target/`."
                ),
                List.of(
                        "Feature files under `src/test/resources/features`",
                        "Step definitions, API classes, and Kafka utilities under `src/test/java/com`",
                        "Node backend and Kafka publisher under `server/`",
                        "Failure Analysis Agent and Setup / Onboarding Agent under `src/main/java/com/analysis`"
                ),
                List.of(),
                "The current framework covers Client, Account, Portfolio, and Transaction CRUD flows, Kafka event validation, relationship validation, negative scenarios, chained business flows, and both Docker and hybrid local execution paths.",
                "If you later connect this to real systems, the same testing layers can stay in place while the mock backend is replaced with real services and the Kafka config is externalized per environment.",
                "",
                "If you want, I can next walk you through Docker setup, local setup, Kafka publishing, or the failure-analysis flow.",
                List.of(
                        "How do I run it in Docker?",
                        "How does Kafka publishing work here?",
                        "Where are the negative relationship scenarios?",
                        "What does the Failure Analysis Agent do?"
                ),
                null,
                chunks
        );
    }

    private AnswerGenerationContext setupRunContext(String question, List<ProjectChunk> chunks, EnvironmentReport environmentReport) {
        String normalized = question.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "docker", "compose")) {
            return new AnswerGenerationContext(
                    QuestionIntent.SETUP_RUN,
                    question,
                    "Use the main Compose file when you want Kafka, the mock API, and the Java test suite to run together in one command.",
                    List.of(DOCKER_FULL_STACK_COMMAND, "docker compose down -v"),
                    List.of(
                            "Docker Desktop and Docker Compose installed",
                            "You are running the commands from the project root"
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "Use `docker-compose.yml` for the full stack and `docker-compose.kafka.yml` only for Kafka-only hybrid local execution.",
                            "The reports are generated by the Java test run, not by starting the Node server alone."
                    ),
                    "The full-stack Docker path starts Kafka, the Node mock API, and the Java suite together.",
                    "",
                    "",
                    "After the run, open `target/cucumber/cucumber-html-reports/overview-features.html` or `target/surefire-reports/index.html`.",
                    List.of(
                            "How do I run only Kafka tests?",
                            "Where are the reports?"
                    ),
                    environmentReport,
                    chunks
            );
        }

        if (containsAny(normalized, "reset", "seed")) {
            return new AnswerGenerationContext(
                    QuestionIntent.SETUP_RUN,
                    question,
                    "Use the built-in reset flow to restore runtime data from `server/seed-data.json` into `server/clients.json`.",
                    List.of("cd server\nnpm run reset-data", "cd server\nnpm start"),
                    List.of(
                            "Node.js and npm available on the host",
                            "Both `server/seed-data.json` and `server/clients.json` exist"
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "The server reads from `clients.json`, not directly from the seed file.",
                            "`npm start` already performs a reset before launching the server."
                    ),
                    "The current framework protects test data through a seed/reset copy flow and uses the same startup behavior in Docker and local execution.",
                    "",
                    "",
                    "If you want to verify the server after reset, call `http://localhost:3000/health` once it starts.",
                    List.of(
                            "How do I run it locally?",
                            "Why can't I run Kafka tests locally?"
                    ),
                    environmentReport,
                    chunks
            );
        }

        return new AnswerGenerationContext(
                QuestionIntent.SETUP_RUN,
                question,
                "For hybrid local execution, start Kafka in Docker, run the Node server locally, and then run Maven from the project root.",
                List.of(START_KAFKA_ONLY_COMMAND, SERVER_START_COMMAND, FULL_SUITE_COMMAND),
                List.of(
                        "Java 17 installed",
                        "Node.js 20+ and npm installed",
                        "Docker available to run Kafka locally"
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "In hybrid local mode, both the server and Maven run should use `localhost:9092`.",
                        "Run Maven from the project root so `pom.xml`, `config.properties`, and TestNG suites resolve correctly."
                ),
                "The framework keeps two Compose files on purpose: one for the full stack and one for Kafka-only hybrid local runs.",
                "",
                "",
                "If you want the fastest demo path instead, use the full Docker command.",
                List.of(
                        "How do I run it in Docker?",
                        "How do I run only negative scenarios?"
                ),
                environmentReport,
                chunks
        );
    }

    private AnswerGenerationContext troubleshootingContext(String question, List<ProjectChunk> chunks,
                                                           EnvironmentReport environmentReport) {
        String normalized = question.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "report empty", "reports empty")) {
            return new AnswerGenerationContext(
                    QuestionIntent.TROUBLESHOOTING,
                    question,
                    "The most common reason for an empty report is that the Node server was started, but Maven tests were not executed, or the tag filter matched no scenarios.",
                    List.of(),
                    List.of(),
                    List.of(
                            "Confirm that you ran a Maven test command, not only `npm start`.",
                            "Check whether your tag filter matches at least one scenario in `src/test/resources/features`.",
                            "Verify that `target/cucumber.json` and `target/surefire-reports/index.html` were produced after the run.",
                            "If you used the rerun suite, make sure `target/rerun.txt` already existed from a previous failed run."
                    ),
                    List.of(),
                    List.of(),
                    List.of(
                            "Starting the mock API alone will not generate Cucumber or Surefire reports.",
                            "A green run can still produce a minimal failure-analysis markdown file that says no failures were found."
                    ),
                    "",
                    "",
                    "",
                    "Run one focused Maven command and then open the report from `target/`.",
                    List.of(
                            "Where are the reports?",
                            "How do I rerun failures?"
                    ),
                    environmentReport,
                    chunks
            );
        }

        if (containsAny(normalized, "server", "api not starting", "health")) {
            return new AnswerGenerationContext(
                    QuestionIntent.TROUBLESHOOTING,
                    question,
                    "The most likely issue is that the Node server did not start cleanly, or `npm start` could not complete the reset-and-launch flow inside `server/`.",
                    List.of(),
                    List.of(),
                    List.of(
                            "Run `cd server && npm install && npm start` and watch for reset or startup errors.",
                            "Confirm that `server/seed-data.json` can be copied into `server/clients.json`.",
                            "Call `curl http://localhost:3000/health` after startup to verify the API is reachable.",
                            "If you are using Docker, remember that the host port is mapped differently than the container port."
                    ),
                    List.of(),
                    List.of(),
                    List.of(
                            "`npm start` already includes the reset step, so seed or write-permission issues can block startup.",
                            "If the API is up but tests still fail, the problem may be Kafka or tag selection rather than server startup."
                    ),
                    "",
                    "",
                    "",
                    "Once the health check passes, rerun the relevant Maven command from the project root.",
                    List.of(
                            "Why can't I run Kafka tests locally?",
                            "How do I run it locally?"
                    ),
                    environmentReport,
                    chunks
            );
        }

        if (containsAny(normalized, "reset", "seed")) {
            return new AnswerGenerationContext(
                    QuestionIntent.TROUBLESHOOTING,
                    question,
                    "The most likely issue is that the runtime data file was not refreshed from the seed file before the server started, or the files are out of sync.",
                    List.of(),
                    List.of(),
                    List.of(
                            "Confirm that both `server/seed-data.json` and `server/clients.json` exist.",
                            "Run `cd server && npm run reset-data` manually and verify that `clients.json` changes.",
                            "Restart the server with `npm start` so the reset step runs again.",
                            "Re-run the test after the server is healthy."
                    ),
                    List.of(),
                    List.of(),
                    List.of(
                            "Editing the runtime file manually can make the next scenario look inconsistent if reset is skipped.",
                            "The server works from `clients.json`, not from the seed file directly."
                    ),
                    "",
                    "",
                    "",
                    "If reset still looks wrong, compare the two JSON files and verify filesystem write permissions.",
                    List.of(
                            "How do I reset test data?",
                            "How do I run it locally?"
                    ),
                    environmentReport,
                    chunks
            );
        }

        return new AnswerGenerationContext(
                QuestionIntent.TROUBLESHOOTING,
                question,
                "The most likely issue is a mismatch between your local Kafka, server, and Maven runtime configuration. In hybrid local mode, Kafka should be reachable at `localhost:9092`, and the server and tests should point to the same topics and broker.",
                List.of(),
                List.of(),
                List.of(
                        "Start Kafka with `docker compose -f docker-compose.kafka.yml up -d` and verify it is still running.",
                        "Check that the server is healthy at `http://localhost:3000/health`.",
                        "Confirm that the server startup env vars and the Maven properties both point to `localhost:9092` and the same topic names.",
                        "If API calls succeed but no event is found, check the server console for `[AI-KAFKA-VALIDATOR][Kafka] Published ...` with the same entity id and event type.",
                        "If the run already failed, open `target/failure-analysis.md` or `target/surefire-reports/failure-analysis.html` for a post-run explanation."
                ),
                List.of(),
                List.of(),
                List.of(
                        "Using `kafka:9092` on the host side will fail in hybrid local mode; that hostname is for containers inside the full Docker stack.",
                        "Kafka validation in this framework matches by business key and event type, not by taking the first message from the topic."
                ),
                "",
                "",
                "",
                "If you want, I can next help you verify the exact command or tag you are running.",
                List.of(
                        "How do I run only Kafka tests?",
                        "How does Kafka publishing work here?"
                ),
                environmentReport,
                chunks
        );
    }

    private AnswerGenerationContext projectNavigationContext(String question, List<ProjectChunk> chunks) {
        String normalized = question.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "negative", "relationship")) {
            return new AnswerGenerationContext(
                    QuestionIntent.PROJECT_NAVIGATION,
                    question,
                    "The negative relationship and dependency scenarios are defined in the feature layer and enforced in the Node backend before Kafka publishing happens.",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "`src/test/resources/features/NegativeValidationE2E.feature`",
                            "`src/test/java/com/step_defs/BusinessValidationStepDefs.java`",
                            "`server/index.js`"
                    ),
                    List.of(),
                    "The feature file expresses the negative scenarios, the step definitions drive the assertions, and the backend contains the actual relationship and delete-dependency rules.",
                    "",
                    "",
                    "If you want, I can also point you to the specific delete-dependency rules for clients and accounts.",
                    List.of(
                            "Which file publishes account events?",
                            "How does Kafka publishing work here?"
                    ),
                    null,
                    chunks
            );
        }

        if (containsAny(normalized, "cross-entity", "business flow")) {
            return new AnswerGenerationContext(
                    QuestionIntent.PROJECT_NAVIGATION,
                    question,
                    "The chained client-account-transaction demo flow is defined in one feature and runs through the existing business-validation step layer and API/Kafka helpers.",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "`src/test/resources/features/CrossEntityBusinessFlow.feature`",
                            "`src/test/java/com/step_defs/BusinessValidationStepDefs.java`",
                            "`src/test/java/com/api`"
                    ),
                    List.of(),
                    "That scenario reuses the same API and Kafka validation utilities instead of introducing a separate flow engine.",
                    "",
                    "",
                    "If you want a shorter demo, I can suggest a focused Kafka or negative tag next.",
                    List.of(
                            "What is the fastest demo flow?",
                            "How do I run only transaction Kafka tests?"
                    ),
                    null,
                    chunks
            );
        }

        if (containsAny(normalized, "failure", "agent")) {
            return new AnswerGenerationContext(
                    QuestionIntent.PROJECT_NAVIGATION,
                    question,
                    "The failure-analysis logic lives under the main analysis package, while the automatic TestNG hook lives in the test layer.",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "`src/main/java/com/analysis/failure`",
                            "`src/test/java/com/analysis/failure/FailureAnalysisExecutionListener.java`"
                    ),
                    List.of(),
                    "The analysis code reads execution artifacts after a failed run and writes markdown and HTML summaries under `target/`.",
                    "",
                    "",
                    "If you want, I can also explain how the onboarding agent differs from the failure-analysis agent.",
                    List.of(
                            "What does the Failure Analysis Agent do?",
                            "Where are the reports?"
                    ),
                    null,
                    chunks
            );
        }

        return new AnswerGenerationContext(
                QuestionIntent.PROJECT_NAVIGATION,
                question,
                "The project is organized by responsibility: feature files under `src/test/resources/features`, Java orchestration under `src/test/java/com`, server-side behavior under `server/`, and local agents under `src/main/java/com/analysis`.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "`src/test/resources/features` for coverage definitions",
                        "`src/test/java/com/step_defs` for step orchestration",
                        "`src/test/java/com/api` for request logic",
                        "`src/test/java/com/kafka` for event consumption and validation",
                        "`server/index.js` and `server/kafkaPublisher.js` for backend rules and event publishing"
                ),
                List.of(),
                "The framework keeps the feature -> step definition -> API class -> backend route -> Kafka validation path explicit rather than hiding it behind extra abstractions.",
                "",
                "",
                "If you want a narrower answer, ask about a specific entity, feature, or rule.",
                List.of(
                        "Which feature files cover cross-entity flows?",
                        "Where are the negative relationship validations implemented?"
                ),
                null,
                chunks
        );
    }

    private AnswerGenerationContext executionHelpContext(String question, List<ProjectChunk> chunks) {
        String normalized = question.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "negative")) {
            return new AnswerGenerationContext(
                    QuestionIntent.EXECUTION_HELP,
                    question,
                    "Use the `@Negative` tag to run the relationship and dependency validation scenarios only.",
                    List.of(NEGATIVE_RUN_COMMAND),
                    List.of(
                            "Kafka should still be running because these scenarios also verify that failed operations do not emit matching events.",
                            "The mock API must be reachable before you start the Maven run."
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "A failed negative scenario can still be a correct backend behavior if the expectation in the feature is wrong.",
                            "If you only start the Node server, you will not get Cucumber or Surefire reports."
                    ),
                    "",
                    "",
                    "",
                    "If you want, I can also help you narrow that down to one exact negative scenario family.",
                    List.of(
                            "Where are the negative relationship validations implemented?",
                            "How do I rerun failures?"
                    ),
                    null,
                    chunks
            );
        }

        if (containsAny(normalized, "report")) {
            return new AnswerGenerationContext(
                    QuestionIntent.EXECUTION_HELP,
                    question,
                    "The main reports are written under `target/` after a Maven run.",
                    List.of(
                            "open target/cucumber-report.html\nopen target/cucumber/cucumber-html-reports/overview-features.html\nopen target/surefire-reports/index.html\nopen target/surefire-reports/failure-analysis.html"
                    ),
                    List.of(
                            "Run a Maven test command first so the report artifacts exist.",
                            "The failure-analysis HTML appears when a failed run produces analysis output."
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "`target/rerun.txt` is for reruns, not a human-friendly report.",
                            "A green run can still produce a minimal failure-analysis markdown file."
                    ),
                    "",
                    "",
                    "",
                    "If you need to rerun only failed scenarios next, use the rerun suite.",
                    List.of(
                            "How do I rerun failures?",
                            "What does the Failure Analysis Agent do?"
                    ),
                    null,
                    chunks
            );
        }

        if (containsAny(normalized, "rerun")) {
            return new AnswerGenerationContext(
                    QuestionIntent.EXECUTION_HELP,
                    question,
                    "Use the rerun TestNG suite after a failed run. It consumes `target/rerun.txt` produced by the main suite.",
                    List.of(RERUN_COMMAND),
                    List.of(
                            "Run the main suite first so `target/rerun.txt` exists.",
                            "Use the same base URI and Kafka topic settings that were used for the original run."
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "If the rerun suite appears to do nothing, the previous run may not have produced failed scenarios.",
                            "If you changed data or environment state before rerunning, failures may shift."
                    ),
                    "",
                    "",
                    "",
                    "If you want root-cause guidance before rerunning, open the failure-analysis report first.",
                    List.of(
                            "What does the Failure Analysis Agent do?",
                            "Where are the reports?"
                    ),
                    null,
                    chunks
            );
        }

        if (containsAny(normalized, "demo")) {
            return new AnswerGenerationContext(
                    QuestionIntent.EXECUTION_HELP,
                    question,
                    "For a short demo, run the chained cross-entity flow first. It tells a clear story across client, account, transaction, and Kafka validation in one scenario path.",
                    List.of(
                            "sh ./mvnw clean test -Dtestng.suite.file=testng.xml -Dcucumber.filter.tags=\"@CrossEntityE2E\" -DbaseURI=http://localhost:3000 -DkafkaBootstrapServers=localhost:9092 -DkafkaClientEventsTopic=client-events -DkafkaAccountEventsTopic=account-events -DkafkaPortfolioEventsTopic=portfolio-events -DkafkaTransactionEventsTopic=transaction-events",
                            "open target/cucumber/cucumber-html-reports/overview-features.html"
                    ),
                    List.of(
                            "Kafka and the mock API should already be running.",
                            "Use the full Docker stack if you want the easiest one-command demo path."
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "The full regression suite is harder to narrate live than one focused flow.",
                            "If you want a Kafka-only follow-up, transaction Kafka scenarios are a good next step."
                    ),
                    "",
                    "",
                    "",
                    "After that, I'd usually show one Kafka-focused tag or the failure-analysis flow.",
                    List.of(
                            "How do I run only transaction Kafka tests?",
                            "What does the Failure Analysis Agent do?"
                    ),
                    null,
                    chunks
            );
        }

        if (containsAny(normalized, "transaction kafka")) {
            return kafkaExecutionContext(question, chunks, TRANSACTION_KAFKA_RUN_COMMAND, "Use the `@KafkaTransactionData` tag to run only transaction Kafka scenarios.");
        }

        return kafkaExecutionContext(question, chunks, ALL_KAFKA_RUN_COMMAND,
                "Use the Kafka tag groups to run Kafka-focused scenarios only. The broadest option is the combined Kafka tag expression across client, account, portfolio, and transaction flows.");
    }

    private AnswerGenerationContext kafkaExecutionContext(String question, List<ProjectChunk> chunks,
                                                          String command, String directAnswer) {
        return new AnswerGenerationContext(
                QuestionIntent.EXECUTION_HELP,
                question,
                directAnswer,
                List.of(command),
                List.of(
                        "Kafka should be running and reachable from the execution mode you are using.",
                        "The server and Maven properties should point to the same broker and topics."
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "If API calls succeed but no event is found, check the server logs for `[AI-KAFKA-VALIDATOR][Kafka] Published ...`.",
                        "In hybrid local mode, use `localhost:9092`; in the full Docker stack, containers use `kafka:9092` internally."
                ),
                "",
                "",
                "",
                "If you want a narrower run, ask for one exact entity or one exact event scenario.",
                List.of(
                        "Why can't I run Kafka tests locally?",
                        "How does Kafka publishing work here?"
                ),
                null,
                chunks
        );
    }

    private AnswerGenerationContext kafkaExplanationContext(String question, List<ProjectChunk> chunks) {
        return new AnswerGenerationContext(
                QuestionIntent.KAFKA_EXPLANATION,
                question,
                "Kafka validation here is part of the end-to-end flow, not a separate afterthought. The Node backend publishes events after successful operations, and the Java side waits for a matching event by business key before comparing metadata and payload with the latest API response.",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "A Cucumber scenario triggers the Java API layer.",
                        "The Node backend route handles the request and applies relationship or delete-dependency validation.",
                        "If the operation succeeds, the backend publishes to the relevant Kafka topic.",
                        "Java Kafka consumers wait for the matching event by `clientId` or `entityId` plus `eventType`.",
                        "Kafka validators compare the topic, key, event type, and payload against the API response.",
                        "Negative scenarios also verify that failed operations do not publish the matching event."
                ),
                List.of(
                        "`server/index.js` for route handling and publish triggers",
                        "`server/kafkaPublisher.js` for the actual Kafka send",
                        "`src/test/java/com/kafka/KafkaEventConsumer.java` for client events",
                        "`src/test/java/com/kafka/EntityKafkaEventConsumer.java` for account, portfolio, and transaction events"
                ),
                List.of(),
                "The framework uses `client-events`, `account-events`, `portfolio-events`, and `transaction-events`, and it matches by business key instead of taking the first available message from a topic.",
                "In a real system, the same validation pattern still works, but secure client config, topic isolation, and test-data correlation usually need more control.",
                "",
                "If you want, I can next point you to the exact file that publishes account or transaction events.",
                List.of(
                        "Which file publishes account events?",
                        "How do I run only transaction Kafka tests?",
                        "Why are no Kafka events found?"
                ),
                null,
                chunks
        );
    }

    private AnswerGenerationContext agentExplanationContext(String question, List<ProjectChunk> chunks) {
        return new AnswerGenerationContext(
                QuestionIntent.AGENT_EXPLANATION,
                question,
                "This project has two local agents with different jobs. The Setup / Onboarding Agent helps you understand, run, troubleshoot, and extend the framework. The Failure Analysis Agent explains why failed runs happened after execution artifacts exist.",
                List.of(ONBOARDING_AGENT_COMMAND, FAILURE_AGENT_COMMAND),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "Setup / Onboarding Agent: repo-aware onboarding, setup guidance, troubleshooting, navigation, and enterprise adaptation guidance",
                        "Failure Analysis Agent: post-run investigation based on `target/cucumber.json`, `target/rerun.txt`, and Surefire artifacts"
                ),
                List.of(),
                "The onboarding agent is question-driven and grounded in project files. The failure-analysis agent is artifact-driven and grounded in execution evidence after a run.",
                "",
                "",
                "If you want, I can help you run one focused scenario and then explain what each agent contributes in that flow.",
                List.of(
                        "How does this framework work?",
                        "How do I run only negative scenarios?",
                        "What does the Failure Analysis Agent output?"
                ),
                null,
                chunks
        );
    }

    private AnswerGenerationContext enterpriseAdaptationContext(String question, List<ProjectChunk> chunks) {
        String normalized = question.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "jenkins", "github actions", "azure devops", "ci/cd", "ci cd")) {
            return new AnswerGenerationContext(
                    QuestionIntent.ENTERPRISE_ADAPTATION,
                    question,
                    "",
                    List.of(),
                    List.of(),
                    List.of(
                            "Start with the full Docker command for one stable smoke job.",
                            "Publish `target/` as build artifacts so reports and failure-analysis output are preserved.",
                            "Add tag-based jobs only after the baseline pipeline is stable.",
                            "If secure environment settings are needed, inject them through CI secrets rather than hardcoding them into commands or files."
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    "The current framework already supports one-command Docker execution, selective execution by Cucumber tags, rerun support, and failure-analysis artifacts under `target/`.",
                    "A real CI/CD setup usually adds environment readiness checks, artifact publishing, secured config injection, and branch- or tag-based suite selection. Cloud runners may also need service containers or Docker-in-Docker support.",
                    "The repository does not currently ship CI pipeline definitions, so pipeline wiring would be a new integration layer rather than a built-in feature.",
                    "A good first step is one pipeline that runs the full Docker stack, archives `target/`, and only then splits into smaller tagged jobs if the team needs that granularity.",
                    List.of(
                            "How do I run it in Docker?",
                            "Where are the reports?"
                    ),
                    null,
                    chunks
            );
        }

        if (containsAny(normalized, "database", "db")) {
            return new AnswerGenerationContext(
                    QuestionIntent.ENTERPRISE_ADAPTATION,
                    question,
                    "",
                    List.of(),
                    List.of(),
                    List.of(
                            "Keep the Java orchestration layer independent of direct database logic where possible.",
                            "Prefer environment-safe test-data provisioning through APIs, fixture services, or dedicated setup jobs.",
                            "Use direct DB access only when it is required for provisioning or verification and is acceptable for the team."
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    "The current framework does not connect directly to a database. Runtime data is file-backed through `server/clients.json`, and reset is driven by copying `server/seed-data.json` into the runtime file.",
                    "A real environment would usually seed data through APIs, fixture services, setup jobs, or isolated test schemas rather than overwriting local JSON files.",
                    "The framework does not currently include JDBC, ORM, or migration-driven provisioning. Any DB integration would be an extension, not an existing feature.",
                    "Choose one repeatable data strategy first, then wire it into setup hooks or pre-test jobs instead of scattering DB setup through step definitions.",
                    List.of(
                            "How do I reset test data?",
                            "How would I adapt this to a real backend?"
                    ),
                    null,
                    chunks
            );
        }

        if (containsAny(normalized, "backend", "real api", "real service")) {
            return new AnswerGenerationContext(
                    QuestionIntent.ENTERPRISE_ADAPTATION,
                    question,
                    "",
                    List.of(),
                    List.of(),
                    List.of(
                            "Keep the Java Cucumber, API, and Kafka validation layers.",
                            "Replace the mock service base URI and topic config with real environment values.",
                            "Provision prerequisite data through real APIs, fixtures, or environment-specific setup jobs instead of local JSON files.",
                            "Move any environment-sensitive values into secure runtime config rather than hardcoded examples."
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    "The project currently talks to a Node.js mock backend built on json-server. Business rules, relationship validation, delete dependency checks, seed/reset behavior, and Kafka publishing are implemented in that mock service.",
                    "A real backend would usually persist to a database, enforce business rules in application services, and publish Kafka events through the production eventing path instead of the mock publisher in `server/kafkaPublisher.js`.",
                    "The current framework assumes deterministic control over backend state. A real shared environment may need stronger cleanup, tenancy, and data-isolation rules.",
                    "A safe path is to point one existing entity flow at a real test environment first, then keep the same validation pattern while replacing the mock setup strategy gradually.",
                    List.of(
                            "How does Kafka publishing work here?",
                            "How would I adapt this to a real Kafka cluster?"
                    ),
                    null,
                    chunks
            );
        }

        return new AnswerGenerationContext(
                QuestionIntent.ENTERPRISE_ADAPTATION,
                question,
                "",
                List.of(),
                List.of(),
                List.of(
                        "Move broker, topic, and environment settings fully into environment-specific config.",
                        "Add secure Kafka client properties for auth and TLS if the real cluster requires them.",
                        "Keep the current API and Kafka validation pattern, but isolate test data more carefully per environment.",
                        "Use CI artifact publishing and tag-based execution so broader suites can scale without becoming all-or-nothing."
                ),
                List.of(),
                List.of(),
                List.of(),
                "The current framework uses a single Kafka broker, plain topic names, and environment-driven configuration through `config.properties`, Docker env vars, and the Java/Node runtime wiring. The mock backend publishes events after successful CRUD operations and the Java side consumes them by business key.",
                "A real Kafka deployment usually adds multiple brokers, secured listeners, authn/authz, environment-specific bootstrap servers, managed topic configuration, and stronger isolation between test and non-test traffic.",
                "The framework does not currently implement Kafka auth, schema registry integration, or multi-broker failover logic. Those would be enterprise adaptations, not current built-in features.",
                "A good first step is to externalize broker and topic config per environment, then add secure Kafka client configuration and CI-safe secrets handling before expanding the suite into shared environments.",
                List.of(
                        "How would I integrate this into CI/CD?",
                        "How does Kafka publishing work here?"
                ),
                null,
                chunks
        );
    }

    private AnswerGenerationContext unknownGeneralContext(String question, List<ProjectChunk> chunks) {
        return new AnswerGenerationContext(
                QuestionIntent.UNKNOWN_BUT_GENERAL,
                question,
                "I can help with framework overview, setup, execution, troubleshooting, project navigation, Kafka validation, and enterprise adaptation. If your question is broad, the easiest starting point is usually how the framework works or how to run it in Docker.",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "Cucumber features drive the Java test layer.",
                        "The Java layer calls the mock API and validates Kafka side effects.",
                        "The Node backend handles CRUD, validation rules, seed/reset, and Kafka publishing.",
                        "Reports and failure-analysis output are written under `target/`."
                ),
                List.of(
                        "Docker and local setup guidance",
                        "Kafka event publishing and validation explanation",
                        "Tagged execution and reports",
                        "Troubleshooting and enterprise adaptation guidance"
                ),
                List.of(),
                "",
                "",
                "",
                "Try one concrete question next and I'll keep the answer grounded in the repository.",
                List.of(
                        "What is this framework?",
                        "How do I run it in Docker?",
                        "How do I run only Kafka tests?",
                        "Why can't I run Kafka tests locally?"
                ),
                null,
                chunks
        );
    }

    private String formatRelevantFiles(QuestionIntent intent, List<ProjectChunk> chunks) {
        if (intent == QuestionIntent.SMALL_TALK || chunks == null || chunks.isEmpty()) {
            return "";
        }

        Set<String> lines = new LinkedHashSet<>();
        for (ProjectChunk chunk : chunks) {
            lines.add("- `" + chunk.path() + "`" + describeTitle(chunk.title()));
            if (lines.size() >= 4) {
                break;
            }
        }

        if (lines.isEmpty()) {
            return "";
        }
        return "Relevant files if you want to inspect the implementation" + System.lineSeparator() + String.join(System.lineSeparator(), lines);
    }

    private String describeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String trimmed = title.trim();
        if (trimmed.matches("[{}()]+")) {
            return "";
        }
        return " - " + trimmed;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
