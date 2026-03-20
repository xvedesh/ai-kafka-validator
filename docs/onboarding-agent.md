# AI Kafka Validator - Setup / Onboarding Agent

The Setup / Onboarding Agent is a framework-aware assistant for understanding, setting up, running, troubleshooting, and extending AI Kafka Validator.

## What Changed in Version 2

The agent no longer behaves like a raw retriever that dumps file references before answering.

Version 2 now:

- classifies user intent first
- retrieves relevant project context from the repository
- generates a human-friendly answer with either:
  - an optional LLM-backed mode
  - or a deterministic fallback mode
- appends a short relevant-files section only when it adds value
- handles broad onboarding questions, small talk, troubleshooting, and enterprise adaptation more gracefully

## Intent Routing

The current intent set is:

- `small_talk`
- `framework_overview`
- `setup_run`
- `troubleshooting`
- `project_navigation`
- `execution_help`
- `kafka_explanation`
- `agent_explanation`
- `enterprise_adaptation`
- `unknown_but_general`

This keeps routing deterministic while still allowing the answer-generation layer to sound natural.

## Retrieval Sources

The agent retrieves from the project itself, including:

- `README.md`
- `docs/*.md`
- `pom.xml`
- `config.properties`
- `docker-compose.yml`
- `docker-compose.kafka.yml`
- `Dockerfile` files
- feature files
- runners
- step definitions
- API classes
- Kafka utilities
- server files
- Failure Analysis Agent files

## Answer Generation Modes

### Deterministic fallback mode

This mode is always available.
It uses intent-aware templates plus retrieved project context.

### Optional LLM-backed mode

If model configuration is present, the onboarding agent will use an LLM to turn the retrieved framework context into a more natural answer while still keeping deterministic routing and references outside the main answer body.

Supported configuration:

- shared `.env` or environment variables: `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_BASE_URL`, `OPENAI_TEMPERATURE`
- onboarding-specific overrides: `ONBOARDING_LLM_API_KEY`, `ONBOARDING_LLM_MODEL`, `ONBOARDING_LLM_BASE_URL`, `ONBOARDING_LLM_TEMPERATURE`

System property equivalents are also supported:

- `-Donboarding.llm.apiKey=...`
- `-Donboarding.llm.model=...`
- `-Donboarding.llm.baseUrl=...`
- `-Donboarding.llm.temperature=...`

If LLM mode is configured but unavailable at runtime, the agent falls back to deterministic mode.

## Entry Points

Run one question directly:

```bash
sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.onboarding.SetupOnboardingAgent -Dagent.question="How does this framework work?"
```

Run with command-line arguments:

```bash
sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.onboarding.SetupOnboardingAgent -Dexec.args="How do I run only negative scenarios?"
```

Run interactive mode:

```bash
sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.onboarding.SetupOnboardingAgent
```

## Example Questions

- `What is this framework?`
- `How does this framework work?`
- `How do I run it in Docker?`
- `How do I run only Kafka tests?`
- `Where are the reports?`
- `How does Kafka publishing work here?`
- `Why can't I run Kafka tests locally?`
- `How would I adapt this to a real Kafka cluster?`
- `How would I integrate this into CI/CD?`

## Environment Checks

For setup and troubleshooting questions, the agent performs lightweight checks such as:

- Java runtime version
- required file presence
- Docker CLI availability
- Docker daemon responsiveness
- Node.js and npm availability
- Kafka port reachability on `localhost:9092`
- mock API health endpoint reachability on `http://localhost:3000/health`

## Relationship to the Failure Analysis Agent

- the Setup / Onboarding Agent helps users understand, set up, run, troubleshoot, and extend the framework
- the Failure Analysis Agent explains why a failed run happened after execution artifacts are produced

Both agents are local. The onboarding agent is question-driven and repo-aware; the failure agent is artifact-driven and post-run focused.
