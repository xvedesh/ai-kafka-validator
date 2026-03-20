# AI Kafka Validator - Failure Analysis Agent

The Failure Analysis Agent is a local framework utility that inspects real execution artifacts and produces a structured failure investigation report.

Current artifact inputs:

- `target/cucumber.json`
- `target/rerun.txt`
- `target/surefire-reports/TestSuite.txt`

Current correlation strategy:

1. find failed scenarios and failed steps from `target/cucumber.json`
2. extract the mapped Java step definition from `match.location`
3. resolve likely framework files:
   - feature file
   - step definition
   - API class
   - Kafka consumer / validator
   - server route
   - config file when relevant
4. classify the failure into a practical root-cause category
5. generate:
   - console output
   - `target/failure-analysis.md`

Current supported categories:

- `API_ASSERTION_FAILURE`
- `KAFKA_EVENT_NOT_FOUND`
- `KAFKA_PAYLOAD_MISMATCH`
- `RELATIONSHIP_VALIDATION_FAILURE`
- `DELETE_DEPENDENCY_FAILURE`
- `CONFIGURATION_ERROR`
- `INFRASTRUCTURE_ERROR`
- `TEST_DATA_ISSUE`
- `UNKNOWN`

Automatic framework trigger:

- after TestNG execution finishes, a listener invokes the Failure Analysis Agent automatically
- if failed scenarios are present, the framework writes:
  - `target/failure-analysis.md`
  - `target/failure-analysis.html`
  - `target/surefire-reports/failure-analysis.html`
  - `target/cucumber/cucumber-html-reports/failure-analysis.html`

Run all detected failures:

```bash
sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.failure.FailureAnalysisAgent
```

Run only the most recent failed scenario:

```bash
sh ./mvnw -q exec:java \
  -Dexec.mainClass=com.analysis.failure.FailureAnalysisAgent \
  -Dfailure.analysis.mode=latest
```

The agent remains deterministic first: category classification, evidence correlation, and code references are still produced locally from framework artifacts.

Optional AI summary mode:

- shared `.env` or environment variables: `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_BASE_URL`, `OPENAI_TEMPERATURE`
- failure-agent-specific overrides: `FAILURE_LLM_API_KEY`, `FAILURE_LLM_MODEL`, `FAILURE_LLM_BASE_URL`, `FAILURE_LLM_TEMPERATURE`

When LLM mode is available, the failure agent adds a short AI summary on top of the deterministic report. If model access is unavailable, it falls back to the deterministic report only without breaking the analysis flow.
