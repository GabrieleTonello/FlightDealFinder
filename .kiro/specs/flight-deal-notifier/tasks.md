# Implementation Plan: Flight Deal Notifier

## Overview

Incremental implementation of the Flight Deal Notifier system, starting with Smithy models and code generation, then building each Lambda handler and business logic component with tests, followed by CDK infrastructure constructs, the CI/CD pipeline, and final integration wiring. Each task builds on the previous, ensuring no orphaned code.

## Tasks

- [x] 1. Set up project structure, Smithy models, and code generation
  - [x] 1.1 Initialize Gradle project under `service/` with Java 25, JUnit 5, jqwik, and Smithy codegen dependencies in `build.gradle`
    - Configure `sourceCompatibility = 25`, add `jqwik` and `junit-jupiter` test dependencies, and Smithy Gradle plugin for Java code generation
    - _Requirements: 20.1, 20.3, 20.4, 20.6_
  - [x] 1.2 Create Smithy model files under `smithy/model/`
    - Create `model.smithy` with all data shapes (FlightDeal, TimeWindow, DateRange, PriceRecord, DealBatchMessage, SearchError, FlightSearchResult, CalendarLookupInput/Output, MatchInput/Output, NotificationInput/Output, WorkflowStartResult) and `services.smithy` with service interfaces (FlightSearchService, CalendarService, FlightMatcherService, NotificationService)
    - _Requirements: 16.1, 16.2_
  - [x] 1.3 Verify Smithy code generation produces Java types
    - Run Gradle build to confirm generated Java types compile and are available in `service/` source sets
    - _Requirements: 16.3, 16.4_

- [ ] 2. Implement Flight Search Lambda handler and business logic
  - [x] 2.1 Create `FlightApiClient.java` proxy under `service/src/main/java/com/flightdeal/proxy/`
    - Implement external flight API client with per-destination querying, returning `List<FlightDeal>` or throwing on error/timeout
    - _Requirements: 2.1, 2.2_
  - [x] 2.2 Create `MetricsEmitter.java` under `service/src/main/java/com/flightdeal/metrics/`
    - Implement CloudWatch custom metric emission for deals found, destinations searched, execution duration, workflows started, start failures, matches found, notifications sent
    - _Requirements: 11.1, 11.2, 11.3_
  - [x] 2.3 Create `FlightSearchHandler.java` under `service/src/main/java/com/flightdeal/handler/`
    - Implement Lambda handler: iterate configured destinations, call FlightApiClient per destination with error isolation, write deals to DynamoDB (retry 3x exponential backoff), publish deal batch to SNS (retry 3x exponential backoff), emit metrics
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 11.1_
  - [ ]* 2.4 Write property test: All configured destinations are queried
    - **Property 1: All configured destinations are queried**
    - **Validates: Requirements 2.1**
  - [ ]* 2.5 Write property test: Deal extraction preserves all required fields
    - **Property 2: Deal extraction preserves all required fields**
    - **Validates: Requirements 2.2, 4.2**
  - [ ]* 2.6 Write property test: Per-destination error isolation
    - **Property 3: Per-destination error isolation**
    - **Validates: Requirements 2.3**
  - [ ]* 2.7 Write property test: DynamoDB write correctness
    - **Property 4: DynamoDB write correctness**
    - **Validates: Requirements 3.1, 3.2**
  - [ ]* 2.8 Write property test: Retry with exponential backoff
    - **Property 5: Retry with exponential backoff**
    - **Validates: Requirements 3.3, 4.3**
  - [ ]* 2.9 Write property test: Deal batch published after storage
    - **Property 6: Deal batch published after storage**
    - **Validates: Requirements 4.1**
  - [ ]* 2.10 Write unit tests for FlightSearchHandler
    - Test success path (deals found for all destinations), partial failures (some destinations fail), all destinations fail, empty results, DynamoDB write retry exhaustion, SNS publish retry exhaustion
    - Mock FlightApiClient, DynamoDB client, SNS client using Mockito
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.3, 4.1, 4.3, 17.1, 17.5_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Implement Workflow Trigger Lambda handler
  - [x] 4.1 Create `WorkflowTriggerHandler.java` under `service/src/main/java/com/flightdeal/handler/`
    - Implement Lambda handler: parse DealBatchMessage from SQS event, start Step Functions execution with deal data as input, emit metrics (workflows started, start failures), throw on StartExecution failure so SQS retries
    - _Requirements: 6.1, 6.2, 6.3, 11.2_
  - [ ]* 4.2 Write property test: Workflow started with correct deal data
    - **Property 7: Workflow started with correct deal data**
    - **Validates: Requirements 6.2**
  - [ ]* 4.3 Write unit tests for WorkflowTriggerHandler
    - Test successful workflow start, StartExecution failure throws error, malformed SQS message handling
    - Mock Step Functions client using Mockito
    - _Requirements: 6.1, 6.2, 6.3, 17.1, 17.5_

- [ ] 5. Implement Calendar Service
  - [x] 5.1 Create `GoogleCalendarClient.java` proxy under `service/src/main/java/com/flightdeal/proxy/`
    - Implement Google Calendar API client: authenticate via OAuth2 credentials from SSM/Secrets Manager, retrieve free/busy windows for a date range
    - _Requirements: 7.1_
  - [x] 5.2 Create `CalendarService.java` under `service/src/main/java/com/flightdeal/service/`
    - Implement calendar lookup: compute date range from earliest departure to latest return across deals, call GoogleCalendarClient, transform response to `List<TimeWindow>`
    - _Requirements: 7.1, 7.3_
  - [ ]* 5.3 Write property test: Calendar date range derived from deals
    - **Property 8: Calendar date range derived from deals**
    - **Validates: Requirements 7.1**
  - [ ]* 5.4 Write property test: Calendar response transformation
    - **Property 9: Calendar response transformation**
    - **Validates: Requirements 7.3**
  - [ ]* 5.5 Write unit tests for CalendarService
    - Test successful calendar lookup, date range calculation from deal list, API error handling
    - Mock GoogleCalendarClient using Mockito
    - _Requirements: 7.1, 7.2, 7.3, 17.1, 17.5_

- [ ] 6. Implement Flight Matcher
  - [x] 6.1 Create `FlightMatcher.java` under `service/src/main/java/com/flightdeal/service/`
    - Implement matching logic: for each deal, check if departure >= window start AND return <= window end; collect matches; sort by price ascending; return matched deals or empty list
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  - [ ]* 6.2 Write property test: Flight matching predicate correctness
    - **Property 10: Flight matching predicate correctness**
    - **Validates: Requirements 8.1, 8.2**
  - [ ]* 6.3 Write property test: Matched deals sorted by price ascending
    - **Property 11: Matched deals sorted by price ascending**
    - **Validates: Requirements 8.3**
  - [ ]* 6.4 Write unit tests for FlightMatcher
    - Test deals within windows, deals outside windows, partial overlaps rejected, empty deals list, empty windows list, price sorting correctness
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 17.2_

- [ ] 7. Implement Notification Service
  - [x] 7.1 Create `NotificationService.java` under `service/src/main/java/com/flightdeal/service/`
    - Implement email notification: format email body with destination, price, departure date, return date, airline for each matched deal; send via Amazon SES to configured recipient
    - _Requirements: 9.1, 9.2_
  - [ ]* 7.2 Write property test: Notification email contains all deal fields
    - **Property 12: Notification email contains all deal fields**
    - **Validates: Requirements 9.1, 9.2**
  - [ ]* 7.3 Write unit tests for NotificationService
    - Test successful email send, email body formatting contains all fields, delivery failure handling
    - Mock SES client using Mockito
    - _Requirements: 9.1, 9.2, 9.3, 17.1, 17.5_

- [ ] 8. Implement MetricsEmitter and property test
  - [ ]* 8.1 Write property test: Metrics emission accuracy
    - **Property 13: Metrics emission accuracy**
    - **Validates: Requirements 11.1, 11.2, 11.3**
  - [ ]* 8.2 Write unit tests for MetricsEmitter
    - Test correct metric names, values, and dimensions emitted for each metric type
    - Mock CloudWatch client using Mockito
    - _Requirements: 11.1, 11.2, 11.3, 17.1, 17.5_

- [x] 9. Checkpoint - Ensure all service code tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Initialize CDK project and create infrastructure constructs
  - [x] 10.1 Initialize CDK TypeScript project under `infra/` with `package.json`, `tsconfig.json`, and CDK app entry point `bin/app.ts`
    - Install `aws-cdk-lib`, `constructs`, and dev dependencies for CDK assertion tests
    - _Requirements: 13.1, 20.2, 20.7_
  - [x] 10.2 Create `scheduling-construct.ts` under `infra/lib/`
    - Define EventBridge rule with `rate(1 hour)` schedule targeting Flight Search Lambda, with retry policy (2 retries)
    - _Requirements: 1.1, 1.2, 1.3_
  - [x] 10.3 Create `data-store-construct.ts` under `infra/lib/`
    - Define DynamoDB table `FlightPriceHistory` with partition key `destination` (String) and sort key `timestamp` (String), on-demand billing
    - _Requirements: 3.1, 3.2_
  - [x] 10.4 Create `messaging-construct.ts` under `infra/lib/`
    - Define SNS Deal Topic, SQS Deal Queue (subscribed to topic, visibility timeout >= 6x Lambda timeout, redrive policy maxReceiveCount=3), SQS Dead Letter Queue (14-day retention)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 14.2_
  - [x] 10.5 Create `compute-construct.ts` under `infra/lib/`
    - Define Flight Search Lambda and Workflow Trigger Lambda with Java 25 runtime, reserved concurrency, memory, timeout settings, IAM roles with least-privilege permissions (DynamoDB write, SNS publish, SQS consume, Step Functions start execution)
    - _Requirements: 14.1, 14.4, 15.1, 15.2, 15.3, 20.1_
  - [ ] 10.6 Create `workflow-construct.ts` under `infra/lib/`
    - Define Step Functions Standard Workflow with CalendarLookup, FlightMatching, SendNotification states, retry policies with exponential backoff per step, failure state handler that logs error details and publishes failure event
    - _Requirements: 10.1, 10.2, 10.3_
  - [ ] 10.7 Create `observability-construct.ts` under `infra/lib/`
    - Define CloudWatch dashboard (Lambda invocations, error rates, DynamoDB capacity, SQS message age/count, DLQ count) and alarms (DLQ > 0, Lambda error rate > 10%, workflow failures > 0, queue age > 2h) with SNS alerting
    - _Requirements: 11.4, 12.1, 12.2, 12.3, 12.4, 12.5, 14.3_

- [ ] 11. Create CI/CD pipeline construct
  - [ ] 11.1 Create `pipeline-construct.ts` under `infra/lib/`
    - Define Build_Pipeline with: build phase (Gradle compile + unit tests + CDK synth), Dev_Stage deployment, integration test execution against Dev_Stage, Prod_Stage deployment gated on integration test success
    - Parameterize environment-specific values per stage using CDK context or SSM parameters
    - _Requirements: 13.2, 13.3, 13.4, 17.3, 17.4, 18.4, 18.5, 19.1, 19.2, 19.3, 19.4, 19.5, 19.6, 20.5, 20.8, 20.9_

- [ ] 12. Wire CDK app entry point and stack composition
  - [ ] 12.1 Update `bin/app.ts` to compose all constructs into a single CDK stack
    - Import and instantiate all constructs (scheduling, data-store, messaging, compute, workflow, observability, pipeline), wire cross-construct references (Lambda ARNs, queue URLs, topic ARNs, state machine ARN)
    - _Requirements: 13.1, 13.2, 15.1_
  - [ ]* 12.2 Write CDK assertion tests
    - Validate EventBridge schedule rate and retry policy, SQS visibility timeout and redrive policy, DLQ retention, Lambda concurrency/memory/timeout, CloudWatch alarm thresholds and actions, IAM least-privilege policies, Step Functions retry policies
    - _Requirements: 1.1, 1.3, 5.2, 5.3, 5.4, 10.1, 12.1, 12.2, 12.3, 12.5, 14.1, 14.4, 15.3_

- [ ] 13. Checkpoint - Ensure CDK synth and all tests pass
  - Ensure all tests pass, CDK synthesizes successfully, ask the user if questions arise.

- [ ] 14. Create integration tests
  - [ ] 14.1 Create `SchedulerIntegrationTest.java` under `integration-tests/src/test/java/com/flightdeal/`
    - Validate that the Scheduler triggers the Flight_Search_Lambda and deal records appear in the Price_Store
    - _Requirements: 18.1_
  - [ ] 14.2 Create `MessagingIntegrationTest.java` under `integration-tests/src/test/java/com/flightdeal/`
    - Validate that messages published to the Deal_Topic are received by the Deal_Queue
    - _Requirements: 18.2_
  - [ ] 14.3 Create `WorkflowIntegrationTest.java` under `integration-tests/src/test/java/com/flightdeal/`
    - Validate that the Workflow_Trigger_Lambda starts the Matching_Workflow when a message is consumed from the Deal_Queue
    - _Requirements: 18.3_

- [ ] 15. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests (jqwik) validate universal correctness properties from the design document
- Unit tests (JUnit 5 + Mockito) validate specific examples and edge cases
- Java 25 for all service code, TypeScript for CDK infrastructure
- All 13 correctness properties from the design are covered by property test tasks
