from __future__ import annotations

from dataclasses import dataclass


FULL_DOCKER_COMMAND = "docker compose up --build --abort-on-container-exit --exit-code-from api-tests"
KAFKA_ONLY_COMMAND = "docker compose -f docker-compose.kafka.yml up -d"
SERVER_START_COMMAND = """cd server
npm install
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \\
KAFKA_CLIENT_EVENTS_TOPIC=client-events \\
KAFKA_ACCOUNT_EVENTS_TOPIC=account-events \\
KAFKA_PORTFOLIO_EVENTS_TOPIC=portfolio-events \\
KAFKA_TRANSACTION_EVENTS_TOPIC=transaction-events \\
npm start"""
FULL_MAVEN_COMMAND = """sh ./mvnw clean test \\
  -Dtestng.suite.file=testng.xml \\
  -DbaseURI=http://localhost:3000 \\
  -DkafkaBootstrapServers=localhost:9092 \\
  -DkafkaClientEventsTopic=client-events \\
  -DkafkaAccountEventsTopic=account-events \\
  -DkafkaPortfolioEventsTopic=portfolio-events \\
  -DkafkaTransactionEventsTopic=transaction-events"""
REPORT_OPEN_COMMANDS = """open target/cucumber-report.html
open target/cucumber/cucumber-html-reports/overview-features.html
open target/surefire-reports/index.html
open target/surefire-reports/failure-analysis.html"""
FAILURE_AGENT_COMMANDS = """sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.failure.FailureAnalysisAgent
sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.failure.FailureAnalysisAgent -Dfailure.analysis.mode=latest"""


@dataclass(frozen=True)
class FrameworkKnowledge:
    project_name: str = "AI Kafka Validator"
    description: str = "AI-Powered Kafka Messaging Validation Testing Framework"

    def summary(self) -> str:
        return "\n".join(
            [
                "Canonical framework truths:",
                "- The repo supports two main execution modes.",
                f"- Full Docker mode uses: {FULL_DOCKER_COMMAND}",
                f"- Hybrid local mode starts Kafka only with: {KAFKA_ONLY_COMMAND}",
                "- In hybrid local mode, the Node mock backend must be started before running the Java test suite.",
                "- The canonical local test command uses the Maven Wrapper (`sh ./mvnw`), not generic `mvn`.",
                "- Reports are written under target/ and the README provides exact macOS open commands.",
                "- The Failure Analysis Agent can be run manually with the Maven Wrapper commands from the README.",
                "- docker-compose.yml is for full-stack execution; docker-compose.kafka.yml is for Kafka-only hybrid execution.",
                "- The framework validates REST API behavior and Kafka event side effects across Client, Account, Portfolio, and Transaction domains.",
            ]
        )


def classify_framework_intent(user_input: str) -> str | None:
    lowered = user_input.lower().strip()

    if any(phrase in lowered for phrase in [
        "setup this framework", "set up this framework", "setup framework", "help me setup", "help me set up",
        "how do i setup", "how do i set up", "how do i run this framework", "how do i run it",
    ]):
        return "SETUP_OVERVIEW"

    if any(phrase in lowered for phrase in [
        "hybrid setup", "hybrid local", "run locally", "local setup", "run tests locally", "execute tests locally",
    ]):
        return "HYBRID_SETUP"

    if any(phrase in lowered for phrase in [
        "open report", "open reports", "how to open report", "how to open reports", "where are the reports",
    ]):
        return "OPEN_REPORTS"

    if any(phrase in lowered for phrase in [
        "run tests", "execute tests", "run the tests locally", "commands to execute the tests locally",
    ]):
        return "RUN_TESTS"

    if any(phrase in lowered for phrase in [
        "failure analysis agent", "rerun failure analysis", "analyze failure",
    ]):
        return "FAILURE_AGENT"

    return None


def build_framework_guidance(intent: str) -> dict:
    if intent == "SETUP_OVERVIEW":
        return {
            "mode": "RESPOND",
            "recommendation": (
                "This repository supports two canonical run modes. The simplest starting point is full Docker, and the second supported path is hybrid local execution. "
                "If you want the fastest setup with the least local wiring, use full Docker. If you want local debugging, use hybrid local and start the Node server before running tests."
            ),
            "reason": (
                "The README explicitly documents both flows. `docker-compose.yml` is the full-stack entrypoint, while `docker-compose.kafka.yml` is the Kafka-only helper for hybrid local execution. "
                "Hybrid mode is not just `mvn test`; it requires Kafka first, then the local Node mock backend, then the Java test suite."
            ),
            "command": (
                "# Full Docker\n"
                + FULL_DOCKER_COMMAND
                + "\n\n# Hybrid local - start Kafka\n"
                + KAFKA_ONLY_COMMAND
                + "\n\n# Hybrid local - start the Node server\n"
                + SERVER_START_COMMAND
                + "\n\n# Hybrid local - run the Java test suite from the project root\n"
                + FULL_MAVEN_COMMAND
            ),
            "what_to_expect": (
                "Full Docker runs Kafka, the mock API, and the Java test suite together. Hybrid local runs Kafka in Docker, the mock API on your host, and the Java suite on your host. "
                "Reports are generated under target/ in both flows."
            ),
            "fallback": "If you are unsure which mode to pick, start with full Docker first and switch to hybrid only if you want local debugging.",
            "next_question": "Do you want to use full Docker or hybrid local?",
        }

    if intent == "HYBRID_SETUP":
        return {
            "mode": "RESPOND",
            "recommendation": "Use the hybrid local flow: start Kafka in Docker, then start the Node mock backend locally, then run the Java test suite with the Maven Wrapper.",
            "reason": "In this repository, hybrid mode is a three-step flow. Kafka-only Docker is not enough by itself; the local server must be running before the Java tests can pass.",
            "command": KAFKA_ONLY_COMMAND + "\n\n" + SERVER_START_COMMAND + "\n\n" + FULL_MAVEN_COMMAND,
            "what_to_expect": "Kafka will be available on localhost:9092, the Node server will run on http://localhost:3000, and the Java suite will write reports under target/.",
            "fallback": f"If you do not need local debugging, use full Docker instead: {FULL_DOCKER_COMMAND}",
            "next_question": "Do you want the hybrid commands only, or also the readiness checks?",
        }

    if intent == "OPEN_REPORTS":
        return {
            "mode": "RESPOND",
            "recommendation": "Use the README report commands from the project root. Those are the canonical macOS commands for this repository.",
            "reason": "The README already defines the exact generated report paths, including the pretty Cucumber HTML report, the Surefire index, and the failure-analysis HTML report.",
            "command": REPORT_OPEN_COMMANDS,
            "what_to_expect": "The HTML reports will open in your default browser if they exist. If a failure-analysis HTML file is missing, it usually means there were no failures or the agent was not rerun manually.",
            "fallback": "If you are in a terminal-only environment, inspect target/failure-analysis.md, target/surefire-reports/*.txt, or target/cucumber.json.",
            "next_question": "Do you want me to explain which report is best for test failures versus Kafka/debug details?",
        }

    if intent == "RUN_TESTS":
        return {
            "mode": "RESPOND",
            "recommendation": "Use the Maven Wrapper command from the README. If you are running locally, make sure the hybrid prerequisites are already up before you start the Java suite.",
            "reason": "This repository uses `sh ./mvnw` as the canonical command and passes explicit runtime properties for base URI and Kafka topics. Generic `mvn clean test` is less reliable here.",
            "command": FULL_MAVEN_COMMAND,
            "what_to_expect": "The suite will execute the Cucumber/TestNG scenarios and generate reports under target/.",
            "fallback": "If you want a one-command run instead, use the full Docker flow from the README.",
            "next_question": "Do you want the full suite command, or a tag-filtered command such as only Kafka or only negative scenarios?",
        }

    if intent == "FAILURE_AGENT":
        return {
            "mode": "RESPOND",
            "recommendation": "Use the Failure Analysis Agent commands from the README to generate or refresh the failure-analysis reports.",
            "reason": "This repository already includes a dedicated failure-analysis flow with Markdown and HTML output under target/.",
            "command": FAILURE_AGENT_COMMANDS,
            "what_to_expect": "The agent will analyze the execution artifacts and write target/failure-analysis.md plus HTML report variants when failures are present.",
            "fallback": "If you only want raw test output, inspect target/surefire-reports and target/cucumber.json directly.",
            "next_question": "Do you want the full failure analysis or only the most recent failed scenario?",
        }

    return {
        "mode": "RESPOND",
        "recommendation": "Use the README as the canonical source for setup and execution, then rely on repo retrieval for code-level details.",
        "reason": "This framework has specific run flows and commands that should not be replaced by generic setup advice.",
        "command": "N/A",
        "what_to_expect": "N/A",
        "fallback": "N/A",
        "next_question": "N/A",
    }
