import { Stack, StackProps, Duration } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import { DataStoreConstruct } from '../constructs/data-store';
import { MessagingConstruct } from '../constructs/messaging';
import { ComputeConstruct } from '../constructs/compute';
import { SchedulingConstruct } from '../constructs/scheduling';
import { WorkflowConstruct } from '../constructs/workflow';
import { ObservabilityConstruct } from '../constructs/observability';
import { AppConfigConstruct } from '../constructs/appconfig';

export interface FlightDealNotifierStackProps extends StackProps {
  readonly stage: string;
}

/**
 * Main stack composing all Flight Deal Notifier constructs.
 * Wires cross-construct references (Lambda ARNs, queue URLs, topic ARNs, state machine ARN).
 *
 * Requirements: 13.1, 13.2, 15.1
 */
export class FlightDealNotifierStack extends Stack {
  constructor(scope: Construct, id: string, props: FlightDealNotifierStackProps) {
    super(scope, id, props);

    // 1. AppConfig — dynamic configuration for flight search settings
    const appConfig = new AppConfigConstruct(this, 'AppConfig', {
      stage: props.stage,
    });

    // 2. Data Store — DynamoDB table for flight price history
    const dataStore = new DataStoreConstruct(this, 'DataStore', {
      stage: props.stage,
    });

    // 2. Messaging — SNS topic, SQS deal queue, DLQ
    const messaging = new MessagingConstruct(this, 'Messaging', {
      stage: props.stage,
    });

    // 3. Compute — Flight Search Lambda + Workflow Trigger Lambda
    //    stateMachineArn is wired after WorkflowConstruct is created (see below)
    const compute = new ComputeConstruct(this, 'Compute', {
      table: dataStore.flightPriceHistoryTable,
      topic: messaging.dealTopic,
      queue: messaging.dealQueue,
      stage: props.stage,
    });

    // 4. Scheduling — EventBridge hourly rule targeting Flight Search Lambda
    new SchedulingConstruct(this, 'Scheduling', {
      flightSearchLambda: compute.flightSearchLambda,
    });

    // 5. Placeholder Lambdas for Step Functions workflow tasks
    //    These point to the same service JAR but with different handler classes.
    //    They will be replaced with dedicated handlers as the service code evolves.
    const codePath = lambda.Code.fromAsset('../service/build/libs/');

    const calendarLambda = new lambda.Function(this, 'CalendarServiceLambda', {
      functionName: `CalendarServiceLambda-${props.stage}`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'com.flightdeal.service.CalendarService',
      code: codePath,
      memorySize: 512,
      timeout: Duration.seconds(120),
    });

    const matcherLambda = new lambda.Function(this, 'FlightMatcherLambda', {
      functionName: `FlightMatcherLambda-${props.stage}`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'com.flightdeal.service.FlightMatcher',
      code: codePath,
      memorySize: 512,
      timeout: Duration.seconds(120),
    });

    const notificationLambda = new lambda.Function(this, 'NotificationServiceLambda', {
      functionName: `NotificationServiceLambda-${props.stage}`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'com.flightdeal.service.NotificationService',
      code: codePath,
      memorySize: 512,
      timeout: Duration.seconds(120),
    });

    // 6. Workflow — Step Functions state machine orchestrating calendar, matcher, notification
    const workflow = new WorkflowConstruct(this, 'Workflow', {
      calendarLambda,
      matcherLambda,
      notificationLambda,
      failureTopic: messaging.dealTopic,
      stage: props.stage,
    });

    // Wire state machine ARN back to Workflow Trigger Lambda (resolves circular dependency)
    compute.workflowTriggerLambda.addEnvironment(
      'STATE_MACHINE_ARN',
      workflow.stateMachine.stateMachineArn,
    );
    compute.workflowTriggerLambda.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ['states:StartExecution'],
        resources: [workflow.stateMachine.stateMachineArn],
      }),
    );

    // Wire AppConfig env vars to Flight Search Lambda
    compute.flightSearchLambda.addEnvironment(
      'APPCONFIG_APPLICATION_ID', appConfig.application.ref);
    compute.flightSearchLambda.addEnvironment(
      'APPCONFIG_ENVIRONMENT_ID', appConfig.environment.ref);
    compute.flightSearchLambda.addEnvironment(
      'APPCONFIG_CONFIGURATION_PROFILE_ID', appConfig.configurationProfile.ref);

    // Grant AppConfig read permissions
    compute.flightSearchLambda.addToRolePolicy(
      new iam.PolicyStatement({
        actions: [
          'appconfig:GetLatestConfiguration',
          'appconfig:StartConfigurationSession',
        ],
        resources: ['*'],
      }),
    );

    // 7. Observability — CloudWatch dashboard and alarms
    new ObservabilityConstruct(this, 'Observability', {
      flightSearchLambda: compute.flightSearchLambda,
      workflowTriggerLambda: compute.workflowTriggerLambda,
      dealQueue: messaging.dealQueue,
      deadLetterQueue: messaging.deadLetterQueue,
      table: dataStore.flightPriceHistoryTable,
      stateMachine: workflow.stateMachine,
      stage: props.stage,
    });
  }
}
