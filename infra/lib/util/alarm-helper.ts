import { Duration } from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatchActions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';

export interface AlarmConfig {
  readonly alarmName: string;
  readonly description: string;
  readonly metric: cloudwatch.IMetric;
  readonly threshold: number;
  readonly evaluationPeriods?: number;
  readonly comparisonOperator?: cloudwatch.ComparisonOperator;
  readonly treatMissingData?: cloudwatch.TreatMissingData;
}

/**
 * Creates a CloudWatch alarm with SNS notification action.
 * Reduces boilerplate when creating multiple alarms with consistent defaults.
 */
export function createAlarmWithNotification(
  scope: Construct,
  id: string,
  config: AlarmConfig,
  alertingTopic: sns.ITopic,
): cloudwatch.Alarm {
  const alarm = new cloudwatch.Alarm(scope, id, {
    alarmName: config.alarmName,
    alarmDescription: config.description,
    metric: config.metric,
    threshold: config.threshold,
    evaluationPeriods: config.evaluationPeriods ?? 1,
    comparisonOperator: config.comparisonOperator ?? cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    treatMissingData: config.treatMissingData ?? cloudwatch.TreatMissingData.NOT_BREACHING,
  });

  alarm.addAlarmAction(new cloudwatchActions.SnsAction(alertingTopic));
  return alarm;
}
