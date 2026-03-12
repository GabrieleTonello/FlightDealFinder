/**
 * Centralized configuration constants for the Flight Deal Notifier infrastructure.
 * Extracted from inline values to improve maintainability and readability.
 */

// ---- Service Identity ----
export const SERVICE_NAME = 'FlightDealNotifier';
export const METRICS_NAMESPACE = 'FlightDealNotifier';

// ---- Lambda Configuration ----
export const FLIGHT_SEARCH_LAMBDA = {
  functionName: 'FlightSearchLambda',
  handler: 'com.flightdeal.handler.FlightSearchHandler',
  memorySize: 512,
  timeoutSeconds: 120,
  reservedConcurrency: 5,
} as const;

export const WORKFLOW_TRIGGER_LAMBDA = {
  functionName: 'WorkflowTriggerLambda',
  handler: 'com.flightdeal.handler.WorkflowTriggerHandler',
  memorySize: 256,
  timeoutSeconds: 60,
  reservedConcurrency: 5,
} as const;

export const WORKFLOW_STEP_LAMBDA = {
  memorySize: 512,
  timeoutSeconds: 120,
} as const;

// ---- DynamoDB Configuration ----
export const DYNAMODB = {
  tableName: 'FlightPriceHistory',
  partitionKey: 'destination',
  sortKey: 'timestamp',
} as const;

// ---- Messaging Configuration ----
export const MESSAGING = {
  topicName: 'FlightDealTopic',
  queueName: 'FlightDealQueue',
  dlqName: 'FlightDealDLQ',
  visibilityTimeoutSeconds: 720, // 6x Workflow Trigger Lambda timeout
  maxReceiveCount: 3,
  dlqRetentionDays: 14,
} as const;

// ---- Scheduling Configuration ----
export const SCHEDULING = {
  rateMinutes: 60,
  retryAttempts: 2,
} as const;

// ---- Step Functions Configuration ----
export const WORKFLOW = {
  stateMachineName: 'FlightDealMatchingWorkflow',
  retryIntervalSeconds: 2,
  retryBackoffRate: 2,
  calendarMaxRetries: 3,
  matcherMaxRetries: 3,
  notificationMaxRetries: 2,
  timeoutMinutes: 15,
} as const;

// ---- Alarm Thresholds ----
export const ALARM_THRESHOLDS = {
  dlqMessageCount: 0,
  lambdaErrorRatePercent: 10,
  workflowFailureCount: 0,
  queueAgeSeconds: 7200, // 2 hours
} as const;

// ---- Alarm Names ----
export const ALARM_NAMES = {
  dlqNonEmpty: 'FlightDeal-DLQ-NonEmpty',
  flightSearchErrorRate: 'FlightDeal-FlightSearch-ErrorRate',
  workflowFailures: 'FlightDeal-Workflow-Failures',
  dealQueueAge: 'FlightDeal-DealQueue-MessageAge',
} as const;

// ---- Alerting ----
export const ALERTING = {
  topicName: 'FlightDealAlertingTopic',
} as const;

// ---- Dashboard ----
export const DASHBOARD = {
  name: 'FlightDealNotifier',
} as const;
