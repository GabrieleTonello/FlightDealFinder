# Requirements Document

## Introduction

The Flight Deal Notifier is a serverless, event-driven service that automatically searches for flight deals on an hourly schedule, stores historical pricing data, and intelligently notifies the user when a cheap flight aligns with free windows in their Google Calendar. The system is designed to be self-healing, observable, and production-grade, leveraging AWS managed services (EventBridge, Lambda, DynamoDB, SNS, SQS, Step Functions, CloudWatch) with Infrastructure as Code.

## Glossary

- **Scheduler**: The Amazon EventBridge scheduled rule that triggers the flight search on a recurring basis
- **Flight_Search_Lambda**: The AWS Lambda function responsible for querying an external flight API and returning flight deal data
- **Price_Store**: The Amazon DynamoDB table that persists flight prices by destination for historical analysis
- **Deal_Topic**: The Amazon SNS topic to which flight deal messages are published
- **Deal_Queue**: The Amazon SQS queue subscribed to the Deal_Topic that buffers messages for downstream processing
- **Dead_Letter_Queue**: The Amazon SQS queue that receives messages from the Deal_Queue after all retry attempts have been exhausted
- **Workflow_Trigger_Lambda**: The AWS Lambda function triggered by the Deal_Queue that initiates the Step Functions workflow
- **Matching_Workflow**: The AWS Step Functions state machine that orchestrates calendar lookup, flight comparison, and notification
- **Calendar_Service**: The component within the Matching_Workflow that retrieves free time windows from the Google Calendar API
- **Flight_Matcher**: The component within the Matching_Workflow that compares available calendar windows with the cheapest flight deals
- **Notification_Service**: The component within the Matching_Workflow that sends email or push notifications to the user
- **Dashboard**: The Amazon CloudWatch dashboard that displays operational metrics and alarm states for the system
- **Smithy_Model**: The set of Smithy interface definition language (IDL) files that define the API models, data shapes, and service interfaces for the system, from which code is generated
- **Build_Pipeline**: The CI/CD pipeline that builds, tests, and deploys the system through sequential stages
- **Dev_Stage**: The development deployment stage in the Build_Pipeline used for validation before production
- **Prod_Stage**: The production deployment stage in the Build_Pipeline that serves live traffic
- **Unit_Test_Suite**: The collection of unit tests covering Lambda function handlers and business logic that execute during the build phase
- **Integration_Test_Suite**: The collection of integration tests that validate deployed system components work together correctly
- **Service_Code**: The Java 25 application code comprising all Lambda function handlers (Flight_Search_Lambda, Workflow_Trigger_Lambda) and business logic components (Calendar_Service, Flight_Matcher, Notification_Service)
- **Infrastructure_Code**: The TypeScript CDK code that defines and provisions all AWS resources
- **Build_Toolchain**: The set of publicly available, open-source tools used to build, test, package, and deploy the system, including Gradle for Service_Code and npm for Infrastructure_Code

## Requirements

### Requirement 1: Scheduled Flight Search Trigger

**User Story:** As a user, I want the system to automatically search for flights every hour, so that I always have up-to-date deal information without manual intervention.

#### Acceptance Criteria

1. THE Scheduler SHALL trigger the Flight_Search_Lambda once every hour using a fixed-rate schedule
2. WHEN the Scheduler triggers the Flight_Search_Lambda, THE Flight_Search_Lambda SHALL begin execution within 60 seconds of the scheduled time
3. IF the Flight_Search_Lambda fails to start after a Scheduler trigger, THEN THE Scheduler SHALL retry the invocation up to 2 additional times

### Requirement 2: Flight Search and Deal Retrieval

**User Story:** As a user, I want the system to search for flights across configured destinations, so that I can discover the cheapest available deals.

#### Acceptance Criteria

1. WHEN the Flight_Search_Lambda is invoked, THE Flight_Search_Lambda SHALL query the external flight API for deals across all configured destinations
2. WHEN the external flight API returns results, THE Flight_Search_Lambda SHALL extract the destination, price, departure date, return date, and airline for each deal
3. IF the external flight API returns an error or times out, THEN THE Flight_Search_Lambda SHALL log the error with the destination and error details and continue processing remaining destinations
4. IF the external flight API returns no results for a destination, THEN THE Flight_Search_Lambda SHALL log an informational message and skip that destination

### Requirement 3: Historical Price Storage

**User Story:** As a user, I want flight prices stored by destination over time, so that I can analyze historical pricing trends.

#### Acceptance Criteria

1. WHEN the Flight_Search_Lambda retrieves flight deals, THE Flight_Search_Lambda SHALL write each deal record to the Price_Store with the destination as the partition key and a timestamp as the sort key
2. THE Price_Store SHALL retain deal records containing destination, price, departure date, return date, airline, and retrieval timestamp
3. IF a write to the Price_Store fails, THEN THE Flight_Search_Lambda SHALL retry the write up to 3 times with exponential backoff before logging the failure

### Requirement 4: Flight Deal Message Publishing

**User Story:** As a user, I want flight deals published to a messaging topic, so that downstream consumers can react to new deals asynchronously.

#### Acceptance Criteria

1. WHEN the Flight_Search_Lambda has stored deal records in the Price_Store, THE Flight_Search_Lambda SHALL publish a message to the Deal_Topic containing the list of deals found in the current run
2. THE Flight_Search_Lambda SHALL include the destination, price, departure date, return date, and airline in each deal within the published message
3. IF publishing to the Deal_Topic fails, THEN THE Flight_Search_Lambda SHALL retry the publish up to 3 times with exponential backoff before logging the failure

### Requirement 5: Message Queuing with Retry and Dead-Letter Handling

**User Story:** As a user, I want deal messages reliably queued with automatic retries and dead-letter handling, so that no deal is silently lost.

#### Acceptance Criteria

1. THE Deal_Queue SHALL subscribe to the Deal_Topic and receive all messages published to the Deal_Topic
2. WHEN a message in the Deal_Queue fails processing, THE Deal_Queue SHALL make the message available for reprocessing up to 3 times before moving the message to the Dead_Letter_Queue
3. THE Dead_Letter_Queue SHALL retain failed messages for a minimum of 14 days
4. THE Deal_Queue SHALL use a visibility timeout of at least 6 times the Workflow_Trigger_Lambda timeout to prevent duplicate processing

### Requirement 6: Workflow Initiation from Queue

**User Story:** As a user, I want a Lambda to consume queued deal messages and start the matching workflow, so that each batch of deals is evaluated against my calendar.

#### Acceptance Criteria

1. WHEN a message arrives in the Deal_Queue, THE Workflow_Trigger_Lambda SHALL be invoked with the message payload
2. WHEN the Workflow_Trigger_Lambda is invoked, THE Workflow_Trigger_Lambda SHALL start an execution of the Matching_Workflow passing the deal data as input
3. IF the Matching_Workflow fails to start, THEN THE Workflow_Trigger_Lambda SHALL throw an error so the message returns to the Deal_Queue for retry

### Requirement 7: Calendar Availability Retrieval

**User Story:** As a user, I want the workflow to check my Google Calendar for free windows, so that deals are only matched against times I am actually available.

#### Acceptance Criteria

1. WHEN the Matching_Workflow starts, THE Calendar_Service SHALL retrieve the user's free and busy windows from the Google Calendar API for the date range covered by the flight deals
2. IF the Google Calendar API returns an error or is unavailable, THEN THE Calendar_Service SHALL retry up to 3 times with exponential backoff before marking the step as failed
3. THE Calendar_Service SHALL return a list of free time windows each containing a start date and end date

### Requirement 8: Flight and Calendar Matching

**User Story:** As a user, I want the system to compare my free calendar windows with the cheapest flights, so that I only get notified about deals I can actually take.

#### Acceptance Criteria

1. WHEN the Calendar_Service returns free windows, THE Flight_Matcher SHALL compare each free window against the available flight deals
2. THE Flight_Matcher SHALL select deals where the departure date and return date fall entirely within a free calendar window
3. THE Flight_Matcher SHALL rank matched deals by price in ascending order
4. IF no deals match any free window, THEN THE Flight_Matcher SHALL log an informational message and end the workflow without sending a notification

### Requirement 9: User Notification

**User Story:** As a user, I want to receive an email notification when a matching deal is found, so that I can act on it promptly.

#### Acceptance Criteria

1. WHEN the Flight_Matcher finds one or more matching deals, THE Notification_Service SHALL send an email to the configured user email address
2. THE Notification_Service SHALL include the destination, price, departure date, return date, and airline for each matching deal in the email body
3. IF the email delivery fails, THEN THE Notification_Service SHALL retry up to 2 times before marking the notification step as failed in the Matching_Workflow

### Requirement 10: Step Functions Workflow Error Handling

**User Story:** As a user, I want the workflow to handle errors gracefully at each step, so that transient failures do not cause the entire workflow to fail permanently.

#### Acceptance Criteria

1. THE Matching_Workflow SHALL define retry policies with exponential backoff for each step in the state machine
2. IF a step in the Matching_Workflow exhausts all retries, THEN THE Matching_Workflow SHALL transition to a failure state that logs the error details including step name, error type, and input payload
3. WHEN the Matching_Workflow transitions to a failure state, THE Matching_Workflow SHALL publish a failure event to enable alerting

### Requirement 11: Observability and Metrics

**User Story:** As a user, I want comprehensive metrics and dashboards, so that I can monitor the health and performance of the system at a glance.

#### Acceptance Criteria

1. THE Flight_Search_Lambda SHALL emit custom CloudWatch metrics for the number of deals found, the number of destinations searched, and the execution duration
2. THE Workflow_Trigger_Lambda SHALL emit custom CloudWatch metrics for the number of workflows started and the number of start failures
3. THE Matching_Workflow SHALL emit custom CloudWatch metrics for the number of matches found and the number of notifications sent
4. THE Dashboard SHALL display metrics for Lambda invocation counts, error rates, DynamoDB read and write capacity usage, SQS message age, SQS approximate message count, and Dead_Letter_Queue message count

### Requirement 12: Alarms and Alerting

**User Story:** As a user, I want alarms that fire when the system is unhealthy, so that I am aware of issues before they impact deal discovery.

#### Acceptance Criteria

1. THE System SHALL create a CloudWatch alarm that triggers when the Dead_Letter_Queue message count exceeds 0
2. THE System SHALL create a CloudWatch alarm that triggers when the Flight_Search_Lambda error rate exceeds 10 percent over a 1-hour period
3. THE System SHALL create a CloudWatch alarm that triggers when the Matching_Workflow failure count exceeds 0 in a 1-hour period
4. WHEN any CloudWatch alarm transitions to the ALARM state, THE System SHALL send a notification to the configured alerting channel
5. THE System SHALL create a CloudWatch alarm that triggers when the Deal_Queue message age exceeds 2 hours

### Requirement 13: Infrastructure as Code

**User Story:** As a developer, I want all infrastructure defined as code, so that the system is reproducible, version-controlled, and deployable through a CI/CD pipeline.

#### Acceptance Criteria

1. THE System SHALL define all AWS resources using AWS CDK with TypeScript
2. THE System SHALL organize infrastructure into logical CDK constructs separating scheduling, data storage, messaging, workflow, and observability concerns
3. THE System SHALL parameterize environment-specific values such as flight API keys, email addresses, and Google Calendar credentials using CDK context or SSM parameters
4. THE System SHALL include a deployment script that provisions the full stack in a single command

### Requirement 14: Self-Healing Architecture

**User Story:** As a user, I want the system to recover automatically from transient failures, so that I do not need to manually intervene when temporary issues occur.

#### Acceptance Criteria

1. THE System SHALL configure Lambda functions with reserved concurrency and memory settings appropriate for the workload
2. THE Deal_Queue SHALL use a redrive policy that moves messages to the Dead_Letter_Queue after the configured maximum receive count
3. WHEN a message lands in the Dead_Letter_Queue, THE System SHALL trigger a CloudWatch alarm to notify the operator
4. THE System SHALL configure all Lambda functions with timeout values that prevent runaway executions

### Requirement 15: AWS Cloud Platform

**User Story:** As a developer, I want the system to run entirely on AWS, so that all components leverage AWS managed services for reliability, scalability, and operational simplicity.

#### Acceptance Criteria

1. THE System SHALL deploy all compute, storage, messaging, orchestration, and observability components on AWS
2. THE System SHALL use only AWS managed services (Lambda, DynamoDB, SNS, SQS, Step Functions, EventBridge, CloudWatch, CDK) for core infrastructure
3. THE System SHALL authenticate and authorize all inter-service communication using AWS IAM roles and policies with least-privilege permissions

### Requirement 16: Smithy Model Definitions

**User Story:** As a developer, I want all API models, data shapes, and service interfaces defined using Smithy, so that data contracts are centralized, type-safe, and code-generated consistently across the system.

#### Acceptance Criteria

1. THE Smithy_Model SHALL define data shapes for flight deals, calendar windows, notification payloads, and workflow inputs and outputs
2. THE Smithy_Model SHALL define service interfaces for the Flight_Search_Lambda, Workflow_Trigger_Lambda, Calendar_Service, Flight_Matcher, and Notification_Service
3. WHEN the Smithy_Model is modified, THE Build_Pipeline SHALL regenerate Java types and validation code from the Smithy_Model before compiling application code
4. THE System SHALL use the generated Java types from the Smithy_Model in all Lambda function handlers and shared business logic to enforce data contract compliance at compile time

### Requirement 17: Unit Tests at Build Time

**User Story:** As a developer, I want unit tests to run during the build phase, so that code defects are caught before deployment and every build produces a verified artifact.

#### Acceptance Criteria

1. THE Unit_Test_Suite SHALL include JUnit 5 tests for each Lambda function handler covering success paths, error paths, and edge cases
2. THE Unit_Test_Suite SHALL include JUnit 5 tests for the Flight_Matcher logic verifying correct matching of deals to calendar windows
3. WHEN the Build_Pipeline executes the build phase, THE Build_Pipeline SHALL run the Unit_Test_Suite using the Build_Toolchain before producing deployment artifacts
4. IF any unit test in the Unit_Test_Suite fails, THEN THE Build_Pipeline SHALL halt the build and report the failing test names and error details
5. THE Unit_Test_Suite SHALL mock external dependencies including the flight API, Google Calendar API, DynamoDB, SNS, and SQS using Java mocking frameworks to ensure tests execute without network access

### Requirement 18: Integration Tests in Pipeline

**User Story:** As a developer, I want integration tests to run in the CI/CD pipeline after deployment, so that I can verify deployed components interact correctly in a real AWS environment.

#### Acceptance Criteria

1. THE Integration_Test_Suite SHALL validate that the Scheduler triggers the Flight_Search_Lambda and that deal records appear in the Price_Store
2. THE Integration_Test_Suite SHALL validate that messages published to the Deal_Topic are received by the Deal_Queue
3. THE Integration_Test_Suite SHALL validate that the Workflow_Trigger_Lambda starts the Matching_Workflow when a message is consumed from the Deal_Queue
4. WHEN the Build_Pipeline completes a deployment to the Dev_Stage, THE Build_Pipeline SHALL execute the Integration_Test_Suite against the Dev_Stage environment
5. IF any integration test in the Integration_Test_Suite fails, THEN THE Build_Pipeline SHALL halt the pipeline and prevent promotion to the Prod_Stage

### Requirement 19: Dev and Prod Pipeline Stages

**User Story:** As a developer, I want separate Dev and Prod deployment stages in the pipeline, so that changes are validated in a non-production environment before reaching production.

#### Acceptance Criteria

1. THE Build_Pipeline SHALL define a Dev_Stage and a Prod_Stage as sequential deployment stages
2. WHEN the Build_Pipeline is triggered, THE Build_Pipeline SHALL deploy to the Dev_Stage first
3. WHEN the Dev_Stage deployment succeeds and all integration tests pass, THE Build_Pipeline SHALL proceed to deploy to the Prod_Stage
4. IF the Dev_Stage deployment fails or any integration test fails, THEN THE Build_Pipeline SHALL halt and prevent deployment to the Prod_Stage
5. THE Build_Pipeline SHALL use separate AWS accounts or isolated resource naming for the Dev_Stage and Prod_Stage to prevent cross-environment interference
6. THE Build_Pipeline SHALL parameterize environment-specific configuration values per stage using CDK context or SSM parameters

### Requirement 20: Technology Stack

**User Story:** As a developer, I want a clearly defined technology stack, so that all service code and infrastructure code use consistent, well-supported languages and runtimes.

#### Acceptance Criteria

1. THE System SHALL implement all Lambda function handlers and business logic (Flight_Search_Lambda, Workflow_Trigger_Lambda, Calendar_Service, Flight_Matcher, Notification_Service) in Java 25
2. THE System SHALL define all infrastructure using AWS CDK with TypeScript
3. THE Unit_Test_Suite SHALL use JUnit 5 as the testing framework for all Service_Code tests
4. THE Build_Pipeline SHALL compile and package the Service_Code using a Java 25 compatible build tool
5. THE Build_Toolchain SHALL use only publicly available, open-source tools for building, packaging, and deploying the system
6. THE Build_Toolchain SHALL use Gradle to build, test, and package the Service_Code
7. THE Build_Toolchain SHALL use npm to install dependencies and synthesize the Infrastructure_Code
8. THE Build_Pipeline SHALL use a publicly available CI/CD service such as AWS CodePipeline or GitHub Actions to orchestrate build, test, and deployment stages
9. THE System SHALL NOT depend on any proprietary or internal build systems, plugins, or tooling that are not available as public open-source software
