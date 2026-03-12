import { Construct } from 'constructs';
import { Duration } from 'aws-cdk-lib';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import { SCHEDULING } from '../configuration/constants';

export interface SchedulingConstructProps {
  readonly flightSearchLambda: lambda.IFunction;
}

export class SchedulingConstruct extends Construct {
  public readonly rule: events.Rule;

  constructor(scope: Construct, id: string, props: SchedulingConstructProps) {
    super(scope, id);

    this.rule = new events.Rule(this, 'HourlyFlightSearchRule', {
      schedule: events.Schedule.rate(Duration.minutes(SCHEDULING.rateMinutes)),
      description: 'Triggers Flight Search Lambda every hour',
    });

    this.rule.addTarget(
      new targets.LambdaFunction(props.flightSearchLambda, {
        retryAttempts: SCHEDULING.retryAttempts,
      }),
    );
  }
}
