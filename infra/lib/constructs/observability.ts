import { Construct } from 'constructs';
import { Duration } from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatchActions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as sfn from 'aws-cdk-lib/aws-stepfunctions';
import { ALARM_THRESHOLDS, ALARM_NAMES, ALERTING, DASHBOARD } from '../configuration/constants';

export interface ObservabilityConstructProps {
  readonly flightSearchLambda: lambda.IFunction;
  readonly workflowTriggerLambda: lambda.IFunction;
  readonly dealQueue: sqs.IQueue;
  readonly deadLetterQueue: sqs.IQueue;
  readonly table: dynamodb.ITable;
  readonly stateMachine: sfn.IStateMachine;
}

export class ObservabilityConstruct extends Construct {
  public readonly dashboard: cloudwatch.Dashboard;
  public readonly alertingTopic: sns.Topic;

  constructor(scope: Construct, id: string, props: ObservabilityConstructProps) {
    super(scope, id);

    this.alertingTopic = new sns.Topic(this, 'AlertingTopic', {
      topicName: ALERTING.topicName,
    });

    const alarmAction = new cloudwatchActions.SnsAction(this.alertingTopic);

    const dlqAlarm = new cloudwatch.Alarm(this, 'DlqMessageCountAlarm', {
      alarmName: ALARM_NAMES.dlqNonEmpty,
      alarmDescription: 'Dead Letter Queue has messages — indicates processing failures',
      metric: props.deadLetterQueue.metricApproximateNumberOfMessagesVisible({
        period: Duration.minutes(1),
        statistic: 'Maximum',
      }),
      threshold: ALARM_THRESHOLDS.dlqMessageCount,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    dlqAlarm.addAlarmAction(alarmAction);

    const flightSearchErrorRate = new cloudwatch.MathExpression({
      expression: '(errors / invocations) * 100',
      usingMetrics: {
        errors: props.flightSearchLambda.metricErrors({ period: Duration.hours(1), statistic: 'Sum' }),
        invocations: props.flightSearchLambda.metricInvocations({ period: Duration.hours(1), statistic: 'Sum' }),
      },
      period: Duration.hours(1),
    });

    const flightSearchErrorAlarm = new cloudwatch.Alarm(this, 'FlightSearchErrorRateAlarm', {
      alarmName: ALARM_NAMES.flightSearchErrorRate,
      alarmDescription: 'Flight Search Lambda error rate exceeds threshold over 1 hour',
      metric: flightSearchErrorRate,
      threshold: ALARM_THRESHOLDS.lambdaErrorRatePercent,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    flightSearchErrorAlarm.addAlarmAction(alarmAction);

    const workflowFailureAlarm = new cloudwatch.Alarm(this, 'WorkflowFailureAlarm', {
      alarmName: ALARM_NAMES.workflowFailures,
      alarmDescription: 'Matching Workflow has failures in the last hour',
      metric: props.stateMachine.metricFailed({ period: Duration.hours(1), statistic: 'Sum' }),
      threshold: ALARM_THRESHOLDS.workflowFailureCount,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    workflowFailureAlarm.addAlarmAction(alarmAction);

    const queueAgeAlarm = new cloudwatch.Alarm(this, 'DealQueueAgeAlarm', {
      alarmName: ALARM_NAMES.dealQueueAge,
      alarmDescription: 'Deal Queue oldest message age exceeds threshold',
      metric: props.dealQueue.metricApproximateAgeOfOldestMessage({ period: Duration.minutes(5), statistic: 'Maximum' }),
      threshold: ALARM_THRESHOLDS.queueAgeSeconds,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    queueAgeAlarm.addAlarmAction(alarmAction);

    this.dashboard = new cloudwatch.Dashboard(this, 'FlightDealDashboard', {
      dashboardName: DASHBOARD.name,
    });

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

    this.dashboard.addWidgets(
      new cloudwatch.GraphWidget({
        title: 'DynamoDB Read Capacity',
        left: [props.table.metricConsumedReadCapacityUnits({ statistic: 'Sum' })],
        width: 12,
      }),
      new cloudwatch.GraphWidget({
        title: 'DynamoDB Write Capacity',
        left: [props.table.metricConsumedWriteCapacityUnits({ statistic: 'Sum' })],
        width: 12,
      }),
    );

    this.dashboard.addWidgets(
      new cloudwatch.GraphWidget({
        title: 'SQS Message Age',
        left: [props.dealQueue.metricApproximateAgeOfOldestMessage({ statistic: 'Maximum' })],
        width: 12,
      }),
      new cloudwatch.GraphWidget({
        title: 'SQS Approximate Message Count',
        left: [props.dealQueue.metricApproximateNumberOfMessagesVisible({ statistic: 'Maximum' })],
        width: 12,
      }),
    );

    this.dashboard.addWidgets(
      new cloudwatch.GraphWidget({
        title: 'Dead Letter Queue Message Count',
        left: [props.deadLetterQueue.metricApproximateNumberOfMessagesVisible({ statistic: 'Maximum' })],
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
