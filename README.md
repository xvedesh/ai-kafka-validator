# AI Kafka Validator

AI Kafka Validator is an AI-powered Kafka messaging validation testing framework designed to validate both REST APIs and asynchronous event-driven flows. It combines RestAssured, Cucumber, TestNG, JSON Server, and Kafka to verify CRUD behavior, event publishing, relationship rules, negative business validations, and cross-entity workflows in a single runnable project.

## Overview

This framework validates both API responses and Kafka event side effects, including business relationship rules, negative scenarios, and chained cross-entity flows.

It also includes two AI-powered agents:

- the Setup / Troubleshooting Agent in `agent/`, which helps users choose the right run mode, execute the framework, open reports, and troubleshoot setup issues
- the Failure Analysis Agent, which inspects real execution artifacts and produces structured root-cause guidance after failed runs

It covers:

- synchronous API response validation
- asynchronous Kafka event validation
- business relationship and dependency checks in the mock backend
- negative scenarios that verify failed operations do not emit matching Kafka events
- repeatable runtime data reset through `seed-data.json -> clients.json`

## Setup / Troubleshooting Agent

The primary setup and troubleshooting assistant for this repository is the Python agent in [agent/](agent). It is designed to help you choose the correct run mode, execute the framework safely, open reports, troubleshoot setup issues, and guide you to the Failure Analysis Agent when you need post-run investigation.

Use this agent first when you want help with:

- setting up the framework on your machine
- choosing between full Docker and hybrid local execution
- running tests and opening reports
- troubleshooting startup, Kafka, or report issues
- understanding the supported setup flow before running commands
- learning how to use the Failure Analysis Agent after a failed run

### Activate and Run the Agent

From the project root:

```bash
cd agent
source .venv/bin/activate
python main.py
```

You will then see:

```text
AI Kafka Validator Setup Assistant
Type 'exit' to quit.
```

### How to Use It

Ask the agent practical setup or troubleshooting questions such as:

- `Help me set up this framework on my machine`
- `Should I use full Docker or hybrid local?`
- `How do I run the tests locally?`
- `How do I open the reports?`
- `Why are Kafka tests failing locally?`
- `How do I use the Failure Analysis Agent?`
- `How do I open the failure analysis report?`

The agent is grounded in repository knowledge and should guide you through the supported framework flows, including:

- full Docker execution through `docker-compose.yml`
- hybrid local execution through `docker-compose.kafka.yml`
- starting the local Node mock server before running hybrid local tests
- opening generated reports under `target/`
- rerunning or inspecting the Failure Analysis Agent output

Optional LLM configuration:

- shared `.env` or environment variables: `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_BASE_URL`, `OPENAI_TEMPERATURE`

Additional Python-agent details are documented in [agent/](agent).


## Failure Analysis Agent

The Failure Analysis Agent is the second AI-powered agent in the framework. It inspects real execution artifacts and produces a structured failure investigation report.

It uses artifacts such as:

- `target/cucumber.json`
- `target/rerun.txt`
- `target/surefire-reports/TestSuite.txt`

Automatic behavior:

- after TestNG execution finishes, the framework runs the Failure Analysis Agent automatically
- if failed scenarios are present, it writes:
  - `target/failure-analysis.md`
  - `target/failure-analysis.html`
  - `target/surefire-reports/failure-analysis.html`
  - `target/cucumber/cucumber-html-reports/failure-analysis.html`

Run the agent manually for all detected failures:

```bash
sh ./mvnw -q exec:java -Dexec.mainClass=com.analysis.failure.FailureAnalysisAgent
```

Run the agent for only the most recent failed scenario:

```bash
sh ./mvnw -q exec:java \
  -Dexec.mainClass=com.analysis.failure.FailureAnalysisAgent \
  -Dfailure.analysis.mode=latest
```

Manual PowerShell command:

```powershell
.\mvnw.cmd -q exec:java --% -Dexec.mainClass=com.analysis.failure.FailureAnalysisAgent
```


## Key Capabilities

- RestAssured-based API automation with Cucumber + TestNG execution
- full CRUD coverage for `Client`, `Account`, `Portfolio`, and `Transaction`
- Kafka E2E validation for successful create, update, patch, and delete flows
- negative business validation scenarios with "no Kafka event published" assertions
- cross-entity business flow coverage across client, account, and transaction
- JWT-based authentication flow against the mock API
- Docker-based execution for Kafka, mock API, and Java test suite
- local execution support with Kafka in Docker and server/tests on host
- protected runtime data via `seed-data.json -> clients.json` reset flow
- HTML, JSON, Pretty Cucumber, and Surefire reports
- automatic and manual failure analysis through the Failure Analysis Agent
- AI-powered guidance through two built-in agents: the Setup / Troubleshooting Agent and the Failure Analysis Agent

## Quick Start

### Docker

Run the full stack:

```bash
docker compose up --build --abort-on-container-exit --exit-code-from api-tests
```

### Local

1. Start Kafka:

```bash
docker compose -f docker-compose.kafka.yml up -d
```

2. Start the mock API:

```bash
cd server
npm install
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
KAFKA_CLIENT_EVENTS_TOPIC=client-events \
KAFKA_ACCOUNT_EVENTS_TOPIC=account-events \
KAFKA_PORTFOLIO_EVENTS_TOPIC=portfolio-events \
KAFKA_TRANSACTION_EVENTS_TOPIC=transaction-events \
npm start
```

3. Run the test suite from the project root:

```bash
sh ./mvnw clean test \
  -Dtestng.suite.file=testng.xml \
  -DbaseURI=http://localhost:3000 \
  -DkafkaBootstrapServers=localhost:9092 \
  -DkafkaClientEventsTopic=client-events \
  -DkafkaAccountEventsTopic=account-events \
  -DkafkaPortfolioEventsTopic=portfolio-events \
  -DkafkaTransactionEventsTopic=transaction-events
```

## Architecture Overview

The framework has three active runtime layers:

- `json-server`
  Purpose: mock API backend with JWT auth, CRUD routes, business validation rules, and Kafka publishing
- `kafka`
  Purpose: single-broker event transport for API lifecycle messages
- `api-tests`
  Purpose: Java test suite that drives API calls, consumes Kafka messages, and validates API-to-event consistency

Execution flow:

1. A Cucumber step triggers an API action through a Java API class.
2. The request hits the Node mock service.
3. The service validates business rules and relationships.
4. The service persists changes into `clients.json`.
5. The service publishes a Kafka event when the operation succeeds.
6. A Java Kafka consumer reads the matching message by business key.
7. The test validates HTTP response, Kafka metadata, and Kafka payload.

## Domain Coverage

Supported entities:

- `Client`
- `Account`
- `Portfolio`
- `Transaction`

API coverage:

- `POST /clients`, `GET /clients/{id}`, `PUT /clients/{id}`, `PATCH /clients/{id}`, `DELETE /clients/{id}`
- `POST /accounts`, `GET /accounts/{id}`, `PUT /accounts/{id}`, `PATCH /accounts/{id}`, `DELETE /accounts/{id}`
- `POST /portfolios`, `GET /portfolios/{id}`, `PUT /portfolios/{id}`, `PATCH /portfolios/{id}`, `DELETE /portfolios/{id}`
- `POST /transactions`, `GET /transactions/{id}`, `PUT /transactions/{id}`, `PATCH /transactions/{id}`, `DELETE /transactions/{id}`

Kafka topics:

- `client-events`
- `account-events`
- `portfolio-events`
- `transaction-events`

Kafka event coverage currently implemented:

- Client: `CLIENT_CREATED`, `CLIENT_UPDATED`, `CLIENT_PATCHED`, `CLIENT_DELETED`
- Account: `ACCOUNT_CREATED`, `ACCOUNT_UPDATED`, `ACCOUNT_PATCHED`, `ACCOUNT_DELETED`
- Portfolio: `PORTFOLIO_CREATED`, `PORTFOLIO_UPDATED`, `PORTFOLIO_PATCHED`, `PORTFOLIO_DELETED`
- Transaction: `TRANSACTION_CREATED`, `TRANSACTION_UPDATED`, `TRANSACTION_PATCHED`, `TRANSACTION_DELETED`

Feature coverage in the repository:

- happy-path CRUD validation through [ClientService.feature](src/test/resources/features/ClientService.feature)
- Kafka E2E validation through:
  - [ClientKafkaE2E.feature](src/test/resources/features/ClientKafkaE2E.feature)
  - [AccountKafkaE2E.feature](src/test/resources/features/AccountKafkaE2E.feature)
  - [PortfolioKafkaE2E.feature](src/test/resources/features/PortfolioKafkaE2E.feature)
  - [TransactionKafkaE2E.feature](src/test/resources/features/TransactionKafkaE2E.feature)
- negative validation coverage through [NegativeValidationE2E.feature](src/test/resources/features/NegativeValidationE2E.feature)
- chained business flow coverage through [CrossEntityBusinessFlow.feature](src/test/resources/features/CrossEntityBusinessFlow.feature)

## Business Rules / Relationships

The Node mock backend enforces business validation rules before persistence and Kafka publishing.

Relationship rules:

- `Account.clientId` must reference an existing client
- `Portfolio.clientId` must reference an existing client
- `Transaction.clientId` must reference an existing client
- `Transaction.accountId` must reference an existing account
- `Transaction.clientId` must match the owner of `Transaction.accountId`

Delete dependency rules:

- Client delete is blocked if dependent transactions exist
- Client delete is blocked if dependent accounts exist
- Client delete is blocked if dependent portfolios exist
- Account delete is blocked if dependent transactions exist
- Portfolio delete is allowed without dependency checks
- Transaction delete is allowed without dependency checks

HTTP behavior used by the current implementation:

- `400 Bad Request` for relationship and business validation failures
- `409 Conflict` for dependency-blocked delete operations
- `404 Not Found` when the target resource itself does not exist

Example server-side messages:

- `Client not found for clientId=...`
- `Account not found for accountId=...`
- `Transaction clientId does not match account owner`
- `Cannot delete client with existing accounts`
- `Cannot delete client with existing portfolios`
- `Cannot delete client with existing transactions`
- `Cannot delete account with existing transactions`

## Kafka Event Validation

Kafka validation is not based on "first message in topic". The framework consumes by matching the business key for the current scenario.

Matching strategy:

- Client events are matched by `clientId` and `eventType`
- Account, Portfolio, and Transaction events are matched by `entityId` and `eventType`

Current event shape:

Client events:

```json
{
  "eventType": "CLIENT_CREATED",
  "entityType": "CLIENT",
  "clientId": "....",
  "timestamp": "...",
  "payload": {}
}
```

Entity events:

```json
{
  "eventType": "ACCOUNT_CREATED",
  "entityType": "ACCOUNT",
  "entityId": "A123456",
  "timestamp": "...",
  "payload": {}
}
```

Successful Kafka scenarios validate:

- expected topic
- expected Kafka key
- expected event type
- expected entity type
- payload equality against the latest API response

Negative Kafka scenarios validate:

- failed operations must not publish the matching Kafka event within the configured timeout

Relevant Kafka support code:

- [server/kafkaPublisher.js](server/kafkaPublisher.js)
- [src/test/java/com/kafka/KafkaEventConsumer.java](src/test/java/com/kafka/KafkaEventConsumer.java)
- [src/test/java/com/kafka/EntityKafkaEventConsumer.java](src/test/java/com/kafka/EntityKafkaEventConsumer.java)
- [src/test/java/com/kafka/KafkaEventValidator.java](src/test/java/com/kafka/KafkaEventValidator.java)
- [src/test/java/com/kafka/EntityKafkaEventValidator.java](src/test/java/com/kafka/EntityKafkaEventValidator.java)

## Project Structure

```text
.
|-- README.md
|-- pom.xml
|-- config.properties
|-- docker-compose.yml
|-- docker-compose.kafka.yml
|-- testng.xml
|-- testng-rerun.xml
|-- src/test/java/com
|   |-- api
|   |-- interfaces
|   |-- kafka
|   |-- pojo
|   |-- runners
|   |-- step_defs
|   `-- utils
|-- src/test/resources/features
|   |-- ClientService.feature
|   |-- ClientKafkaE2E.feature
|   |-- AccountKafkaE2E.feature
|   |-- PortfolioKafkaE2E.feature
|   |-- TransactionKafkaE2E.feature
|   |-- NegativeValidationE2E.feature
|   `-- CrossEntityBusinessFlow.feature
`-- server
    |-- index.js
    |-- kafkaPublisher.js
    |-- resetData.js
    |-- seed-data.json
    |-- clients.json
    |-- package.json
    `-- Dockerfile
```

## Prerequisites

Common:

- Docker Desktop
- Docker Compose

Local execution:

- Java 17
- Node.js 20+

## Configuration

Default configuration lives in [config.properties](config.properties):

```properties
baseURI=http://localhost:3000
kafkaBootstrapServers=localhost:9092
kafkaClientEventsTopic=client-events
kafkaAccountEventsTopic=account-events
kafkaPortfolioEventsTopic=portfolio-events
kafkaTransactionEventsTopic=transaction-events
kafkaConsumerTimeoutMs=10000
kafkaPollIntervalMs=500
kafkaConsumerGroupPrefix=ai-kafka-validator-tests
username=user1
password=password1
authEndPoint=/auth/login
allClientsEndPoint=/clients
clientEndPoint=/clients/{clientId}
allAccountsEndPoint=/accounts
accountEndPoint=/accounts/{clientId}
allTransactionsEndPoint=/transactions
transactionEndPoint=/transactions/{clientId}
allPortfoliosEndPoint=/portfolios
portfolioEndPoint=/portfolios/{clientId}
```

Resolution order in the Java test layer:

1. JVM system properties
2. environment variables
3. `config.properties`

Key runtime environment variables used by the server and Docker:

- `KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_CLIENT_EVENTS_TOPIC`
- `KAFKA_ACCOUNT_EVENTS_TOPIC`
- `KAFKA_PORTFOLIO_EVENTS_TOPIC`
- `KAFKA_TRANSACTION_EVENTS_TOPIC`
- `BASE_URI`

## Test Data / Seed Reset

The framework protects mock data through a seed/reset mechanism.

Current data files:

- [server/seed-data.json](server/seed-data.json)
  Purpose: immutable source dataset
- [server/clients.json](server/clients.json)
  Purpose: runtime file used by `json-server`

Current reset behavior:

- `npm run reset-data` copies `seed-data.json` into `clients.json`
- `npm start` automatically executes `npm run reset-data && node index.js`
- Docker uses the same `npm start`, so runtime data is restored there too

Manual reset command:

```bash
cd server
npm run reset-data
```

## How to Run

### Docker Usage

Two Compose files are kept intentionally:

- [docker-compose.yml](docker-compose.yml) runs the full stack: Kafka, mock API, and Java tests
- [docker-compose.kafka.yml](docker-compose.kafka.yml) starts Kafka only for hybrid local execution

Use the main Compose file when you want one-command execution. Use the Kafka-only file when you want to run the Node server and Maven tests directly on the host.

### Option 1: Full Docker Execution

Run the full stack:

```bash
docker compose up --build --abort-on-container-exit --exit-code-from api-tests
```

Optionally save the run log:

```bash
docker compose up --build --abort-on-container-exit --exit-code-from api-tests | tee run.log
```

Stop and clean containers:

```bash
docker compose down -v
```

### Option 2: Hybrid Local Execution

Start Kafka only:

```bash
docker compose -f docker-compose.kafka.yml up -d
```

Start the mock API:

```bash
cd server
npm install
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
KAFKA_CLIENT_EVENTS_TOPIC=client-events \
KAFKA_ACCOUNT_EVENTS_TOPIC=account-events \
KAFKA_PORTFOLIO_EVENTS_TOPIC=portfolio-events \
KAFKA_TRANSACTION_EVENTS_TOPIC=transaction-events \
npm start
```

Run the full Java suite from the project root:

```bash
sh ./mvnw clean test \
  -Dtestng.suite.file=testng.xml \
  -DbaseURI=http://localhost:3000 \
  -DkafkaBootstrapServers=localhost:9092 \
  -DkafkaClientEventsTopic=client-events \
  -DkafkaAccountEventsTopic=account-events \
  -DkafkaPortfolioEventsTopic=portfolio-events \
  -DkafkaTransactionEventsTopic=transaction-events
```

Stop Kafka:

```bash
docker compose -f docker-compose.kafka.yml down -v
```

### Option 3: Windows PowerShell

Start Kafka:

```powershell
docker compose -f docker-compose.kafka.yml up -d
```

Start the mock API:

```powershell
cd server
npm install
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:KAFKA_CLIENT_EVENTS_TOPIC="client-events"
$env:KAFKA_ACCOUNT_EVENTS_TOPIC="account-events"
$env:KAFKA_PORTFOLIO_EVENTS_TOPIC="portfolio-events"
$env:KAFKA_TRANSACTION_EVENTS_TOPIC="transaction-events"
npm start
```

Run the suite from the project root:

```powershell
.\mvnw.cmd clean test --% -Dtestng.suite.file=testng.xml -DbaseURI=http://localhost:3000 -DkafkaBootstrapServers=localhost:9092 -DkafkaClientEventsTopic=client-events -DkafkaAccountEventsTopic=account-events -DkafkaPortfolioEventsTopic=portfolio-events -DkafkaTransactionEventsTopic=transaction-events
```

### Readiness Checks

Health endpoint:

```bash
curl http://localhost:3000/health
```

Auth smoke check:

```bash
curl -X POST http://localhost:3000/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user1","password":"password1"}'
```

## Tags and Execution Strategy

Current tag groups in the project:

- `@ClientData`
  Purpose: CRUD regression outline for client, account, portfolio, transaction
- `@KafkaClientData`
  Purpose: all client Kafka E2E scenarios
- `@KafkaAccountData`
  Purpose: all account Kafka E2E scenarios
- `@KafkaPortfolioData`
  Purpose: all portfolio Kafka E2E scenarios
- `@KafkaTransactionData`
  Purpose: all transaction Kafka E2E scenarios
- `@Negative`
  Purpose: negative business validation suite
- `@BusinessFlow`
  Purpose: business-flow scenarios
- `@CrossEntityE2E`
  Purpose: chained client-account-transaction flow

Entity-level Kafka tags:

- Client:
  `@KafkaCreateClient`, `@KafkaUpdateClient`, `@KafkaPatchClient`, `@KafkaDeleteClient`
- Account:
  `@KafkaCreateAccount`, `@KafkaUpdateAccount`, `@KafkaDeleteAccount`
- Portfolio:
  `@KafkaCreatePortfolio`, `@KafkaPatchPortfolio`, `@KafkaDeletePortfolio`
- Transaction:
  `@KafkaCreateTransaction`, `@KafkaUpdateTransaction`, `@KafkaDeleteTransaction`

Run only happy-path CRUD regression:

```bash
sh ./mvnw clean test \
  -Dtestng.suite.file=testng.xml \
  -Dcucumber.filter.tags="@ClientData" \
  -DbaseURI=http://localhost:3000 \
  -DkafkaBootstrapServers=localhost:9092 \
  -DkafkaClientEventsTopic=client-events \
  -DkafkaAccountEventsTopic=account-events \
  -DkafkaPortfolioEventsTopic=portfolio-events \
  -DkafkaTransactionEventsTopic=transaction-events
```

Run only Kafka scenarios:

```bash
sh ./mvnw clean test \
  -Dtestng.suite.file=testng.xml \
  -Dcucumber.filter.tags="@KafkaClientData or @KafkaAccountData or @KafkaPortfolioData or @KafkaTransactionData" \
  -DbaseURI=http://localhost:3000 \
  -DkafkaBootstrapServers=localhost:9092 \
  -DkafkaClientEventsTopic=client-events \
  -DkafkaAccountEventsTopic=account-events \
  -DkafkaPortfolioEventsTopic=portfolio-events \
  -DkafkaTransactionEventsTopic=transaction-events
```

Run negative validation:

```bash
sh ./mvnw clean test \
  -Dtestng.suite.file=testng.xml \
  -Dcucumber.filter.tags="@Negative" \
  -DbaseURI=http://localhost:3000 \
  -DkafkaBootstrapServers=localhost:9092 \
  -DkafkaClientEventsTopic=client-events \
  -DkafkaAccountEventsTopic=account-events \
  -DkafkaPortfolioEventsTopic=portfolio-events \
  -DkafkaTransactionEventsTopic=transaction-events
```

Run chained business flow:

```bash
sh ./mvnw clean test \
  -Dtestng.suite.file=testng.xml \
  -Dcucumber.filter.tags="@CrossEntityE2E" \
  -DbaseURI=http://localhost:3000 \
  -DkafkaBootstrapServers=localhost:9092 \
  -DkafkaClientEventsTopic=client-events \
  -DkafkaAccountEventsTopic=account-events \
  -DkafkaPortfolioEventsTopic=portfolio-events \
  -DkafkaTransactionEventsTopic=transaction-events
```

Run one exact Kafka scenario:

```bash
sh ./mvnw clean test \
  -Dtestng.suite.file=testng.xml \
  -Dcucumber.filter.tags="@KafkaDeleteClient" \
  -DbaseURI=http://localhost:3000 \
  -DkafkaBootstrapServers=localhost:9092 \
  -DkafkaClientEventsTopic=client-events \
  -DkafkaAccountEventsTopic=account-events \
  -DkafkaPortfolioEventsTopic=portfolio-events \
  -DkafkaTransactionEventsTopic=transaction-events
```

Current runner behavior:

- without `-Dcucumber.filter.tags=...`, the runner executes all features
- with `-Dcucumber.filter.tags=...`, execution is filtered at runtime

## Reporting

Generated report artifacts:

- [target/cucumber-report.html](target/cucumber-report.html)
- [target/cucumber/cucumber-html-reports/overview-features.html](target/cucumber/cucumber-html-reports/overview-features.html)
- [target/cucumber.json](target/cucumber.json)
- [target/surefire-reports/index.html](target/surefire-reports/index.html)
- [target/rerun.txt](target/rerun.txt)
- [target/failure-analysis.md](target/failure-analysis.md)
- [target/surefire-reports/failure-analysis.html](target/surefire-reports/failure-analysis.html)
- [target/cucumber/cucumber-html-reports/failure-analysis.html](target/cucumber/cucumber-html-reports/failure-analysis.html)

Open reports on macOS:

```bash
open target/cucumber-report.html
open target/cucumber/cucumber-html-reports/overview-features.html
open target/surefire-reports/index.html
open target/surefire-reports/failure-analysis.html
```

Open reports on Windows PowerShell:

```powershell
start target\cucumber-report.html
start target\cucumber\cucumber-html-reports\overview-features.html
start target\surefire-reports\index.html
start target\surefire-reports\failure-analysis.html
```

Rerun failed scenarios:

```bash
sh ./mvnw test \
  -Dtestng.suite.file=testng-rerun.xml \
  -DbaseURI=http://localhost:3000 \
  -DkafkaBootstrapServers=localhost:9092 \
  -DkafkaClientEventsTopic=client-events \
  -DkafkaAccountEventsTopic=account-events \
  -DkafkaPortfolioEventsTopic=portfolio-events \
  -DkafkaTransactionEventsTopic=transaction-events
```

## Enterprise Adoption / How to Extend

Safe extension points already present:

- add a new API domain by creating:
  - one POJO
  - one `*API.java`
  - one feature file
  - Kafka assertions through the existing entity Kafka support
- add new business rules in the Node server before persistence
- add new negative validation scenarios without refactoring the framework core
- add more chained E2E flows across domains
- scale selective execution using existing tag groups

Suggested extension path:

1. add the new entity to `server/seed-data.json`
2. expose routes in `server/index.js`
3. add the API class and POJO
4. add feature coverage
5. add Kafka topic and event validation only if the entity emits events

## Framework Scope

AI Kafka Validator combines REST API validation, Kafka event verification, mock backend business rules, and repeatable test data reset in a single runnable framework.

It currently covers:

- CRUD API validation across four related domains
- event-driven validation for successful operations
- relationship and dependency enforcement in the mock backend
- negative scenarios that verify failed requests do not publish matching Kafka events
- chained cross-entity flow validation
- local, hybrid, and Docker-based execution paths
