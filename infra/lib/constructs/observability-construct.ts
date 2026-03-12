import { Construct } from 'constructs';
import { Duration } from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatchActions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as sfn from 'aws-cdk-lib/aws-stepfunctions';

export interface ObservabilityConstructProps {
  /** Flight Search Lambda function */
  readonly flightSearchLambda: lambda.IFunction;
  /** Workflow Trigger Lambda function */
  readonly workflowTriggerLambda: lambda.IFunction;
  /** SQS Deal Queue */
  readonly dealQueue: sqs.IQueue;
  /** SQS Dead Letter Queue */
  readonly deadLetterQueue: sqs.IQueue;
  /** DynamoDB Price Store table */
  readonly table: dynamodb.ITable;
  /** Step Functions Matching Workflow state machine */
  readonly stateMachine: sfn.IStateMachine;
}

export class ObservabilityConstruct extends Construct {
  public readonly dashboard: cloudwatch.Dashboard;
  public readonly alertingTopic: sns.Topic;

  constructor(scope: Construct, id: string, props: ObservabilityConstructProps) {
    super(scope, id);

    // --- Alerting SNS Topic (Req 12.4) ---
    this.alertingTopic = new sns.Topic(this, 'AlertingTopic', {
      topicName: 'FlightDealAlertingTopic',
    });

    const alarmAction = new cloudwatchActions.SnsAction(this.alertingTopic);

    // =====================
    // Alarms
    // =====================

    // Alarm 1: DLQ message count > 0 (Req 12.1, 14.3)
    const dlqAlarm = new cloudwatch.Alarm(this, 'DlqMessageCountAlarm', {
      alarmName: 'FlightDeal-DLQ-NonEmpty',
      alarmDescription: 'Dead Letter Queue has messages — indicates processing failures',
      metric: props.deadLetterQueue.metricApproximateNumberOfMessagesVisible({
        period: Duration.minutes(1),
        statistic: 'Maximum',
      }),
      threshold: 0,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    dlqAlarm.addAlarmAction(alarmAction);

    // Alarm 2: Flight Search Lambda error rate > 10% over 1 hour (Req 12.2)
    const flightSearchErrorRate = new cloudwatch.MathExpression({
      expression: '(errors / invocations) * 100',
      usingMetrics: {
        errors: props.flightSearchLambda.metricErrors({
          period: Duration.hours(1),
          statistic: 'Sum',
        }),
        invocations: props.flightSearchLambda.metricInvocations({
          period: Duration.hours(1),
          statistic: 'Sum',
        }),
      },
      period: Duration.hours(1),
    });

    const flightSearchErrorAlarm = new cloudwatch.Alarm(this, 'FlightSearchErrorRateAlarm', {
      alarmName: 'FlightDeal-FlightSearch-ErrorRate',
      alarmDescription: 'Flight Search Lambda error rate exceeds 10% over 1 hour',
      metric: flightSearchErrorRate,
      threshold: 10,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    flightSearchErrorAlarm.addAlarmAction(alarmAction);

    // Alarm 3: Matching Workflow failure count > 0 in 1 hour (Req 12.3)
    const workflowFailureAlarm = new cloudwatch.Alarm(this, 'WorkflowFailureAlarm', {
      alarmName: 'FlightDeal-Workflow-Failures',
      alarmDescription: 'Matching Workflow has failures in the last hour',
      metric: props.stateMachine.metricFailed({
        period: Duration.hours(1),
        statistic: 'Sum',
      }),
      threshold: 0,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    workflowFailureAlarm.addAlarmAction(alarmAction);

    // Alarm 4: Deal Queue message age > 2 hours (Req 12.5)
    const queueAgeAlarm = new cloudwatch.Alarm(this, 'DealQueueAgeAlarm', {
      alarmName: 'FlightDeal-DealQueue-MessageAge',
      alarmDescription: 'Deal Queue oldest message age exceeds 2 hours',
      metric: props.dealQueue.metricApproximateAgeOfOldestMessage({
        period: Duration.minutes(5),
        statistic: 'Maximum',
      }),
      threshold: Duration.hours(2).toSeconds(),
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    queueAgeAlarm.addAlarmAction(alarmAction);

    // =====================
    // Dashboard (Req 11.4)
    // =====================

    this.dashboard = new cloudwatch.Dashboard(this, 'FlightDealDashboard', {
      dashboardName: 'FlightDealNotifier',
    });

    // Row 1: Lambda invocation counts and error rates
    this.dashboard.addWidgets(
      new cloudwatch.GraphWidget({
        title: 'Lambda Invocations',
        left: [
          props.flightSearchLambda.metricInvocations({ statistic: 'Sum' }),
          props.workflowTriggerLambda.metricInvocations({ statistic: 'Sum' }),
        ],
        width: 12,
      }),
      new cloudwatch.GraphWidget({
        title: 'Lambda Error Rates',
        left: [
          props.flightSearchLambda.metricErrors({ statistic: 'Sum' }),
          props.workflowTriggerLambda.metricErrors({ statistic: 'Sum' }),
        ],
        width: 12,
      }),
    );

    // Row 2: DynamoDB read/write capacity usage
    this.dashboard.addWidgets(
      new cloudwatch.GraphWidget({
        title: 'DynamoDB Read Capacity',
        left: [
          props.table.metricConsumedReadCapacityUnits({ statistic: 'Sum' }),
        ],
        width: 12,
      }),
      new cloudwatch.GraphWidget({
        title: 'DynamoDB Write Capacity',
        left: [
          props.table.metricConsumedWriteCapacityUnits({ statistic: 'Sum' }),
        ],
        width: 12,
      }),
    );

    // Row 3: SQS message age and approximate message count
    this.dashboard.addWidgets(
      new cloudwatch.GraphWidget({
        title: 'SQS Message Age',
        left: [
          props.dealQueue.metricApproximateAgeOfOldestMessage({ statistic: 'Maximum' }),
        ],
        width: 12,
      }),
      new cloudwatch.GraphWidget({
        title: 'SQS Approximate Message Count',
        left: [
          props.dealQueue.metricApproximateNumberOfMessagesVisible({ statistic: 'Maximum' }),
        ],
        width: 12,
      }),
    );

    // Row 4: Dead Letter Queue message count
    this.dashboard.addWidgets(
      new cloudwatch.GraphWidget({
        title: 'Dead Letter Queue Message Count',
        left: [
          props.deadLetterQueue.metricApproximateNumberOfMessagesVisible({ statistic: 'Maximum' }),
        ],
        width: 12,
      }),
      new cloudwatch.AlarmStatusWidget({
        title: 'Alarm Status',
        alarms: [dlqAlarm, flightSearchErrorAlarm, workflowFailureAlarm, queueAgeAlarm],
        width: 12,
      }),
    );
  }
}
