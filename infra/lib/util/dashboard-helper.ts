import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';

/**
 * Creates a pair of graph widgets (left/right) for a dashboard row.
 * Standardizes widget width to 12 (half dashboard width).
 */
export function createDashboardRow(
  leftTitle: string,
  leftMetrics: cloudwatch.IMetric[],
  rightTitle: string,
  rightMetrics: cloudwatch.IMetric[],
): cloudwatch.IWidget[] {
  return [
    new cloudwatch.GraphWidget({
      title: leftTitle,
      left: leftMetrics,
      width: 12,
    }),
    new cloudwatch.GraphWidget({
      title: rightTitle,
      left: rightMetrics,
      width: 12,
    }),
  ];
}
