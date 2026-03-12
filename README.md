# Flight Deal Notifier

A serverless, event-driven system on AWS that automatically discovers cheap flights, checks them against your Google Calendar availability, and sends email notifications for actionable deals.

## Purpose

This project was built from zero using **Spec Driven Development** — a methodology where you start with a formal specification (requirements, design, correctness properties) and systematically implement the system from that spec. Every component, test, and infrastructure resource traces back to a documented requirement.

The spec lives in `.kiro/specs/flight-deal-notifier/` and includes:
- `requirements.md` — 20 user stories with acceptance criteria
- `design.md` — architecture, data models, Smithy IDL contracts, correctness properties
- `tasks.md` — incremental implementation plan with requirement traceability

## How It Works

1. **EventBridge** fires hourly, triggering a Lambda to search for flights
2. **Flight Search Lambda** queries an external API, stores deals in DynamoDB, publishes to SNS
3. **SQS** buffers deal messages with retry and dead-letter handling
4. **Workflow Trigger Lambda** consumes from SQS and starts a Step Functions workflow
5. **Matching Workflow** checks Google Calendar availability, matches deals to free windows, emails you via SES

## Project Structure

```
├── models/              # Smithy IDL data shapes and service interfaces
├── service/             # Java 25 service code (Lambda handlers, business logic, tests)
│   ├── src/main/java/com/flightdeal/
│   │   ├── handler/     # Lambda handlers (FlightSearch, WorkflowTrigger)
│   │   ├── service/     # Business logic (CalendarService, FlightMatcher, NotificationService)
│   │   ├── proxy/       # External API clients (FlightApi, GoogleCalendar)
│   │   ├── dao/         # Data access (PriceRecordDao, DynamoDB Enhanced Client)
│   │   ├── guice/       # Guice DI modules
│   │   └── metrics/     # CloudWatch metrics emission
│   └── src/test/java/com/flightdeal/
│       ├── handler/     # Unit tests for handlers
│       ├── service/     # Unit tests for services
│       ├── proxy/       # Unit tests for API clients
│       ├── dao/         # Unit tests for DAOs
│       ├── metrics/     # Unit tests for metrics
│       └── property/    # jqwik property-based tests (13 correctness properties)
├── infra/               # CDK TypeScript infrastructure
│   ├── lib/constructs/  # CDK constructs (scheduling, data-store, messaging, compute, workflow, observability, pipeline)
│   ├── lib/stacks/      # CDK stack composition
│   └── test/            # CDK assertion tests
└── integration-tests/   # Integration tests for deployed environments
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Compute | AWS Lambda (Java 17) |
| Orchestration | AWS Step Functions |
| Messaging | SNS → SQS with DLQ |
| Storage | DynamoDB (Enhanced Client, @DynamoDbBean) |
| Email | Amazon SES |
| IaC | AWS CDK (TypeScript) |
| Data Contracts | Smithy IDL with Java codegen |
| DI | Google Guice |
| Logging | SLF4J + Log4j2 (CloudWatch) |
| Build | Gradle (Java), npm (CDK) |
| Testing | JUnit 5, jqwik (PBT), Mockito, CDK Assertions |
| Code Quality | Lombok, JaCoCo (80% coverage) |

## Build & Test

```bash
# Service (Java)
cd service
./gradlew clean build

# Infrastructure (CDK)
cd infra
npm ci
npm test
npx cdk synth

# Integration tests (requires deployed environment)
cd integration-tests
./gradlew test
```
