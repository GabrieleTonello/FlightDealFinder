import { Construct } from 'constructs';
import { Duration } from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as sfn from 'aws-cdk-lib/aws-stepfunctions';
import * as tasks from 'aws-cdk-lib/aws-stepfunctions-tasks';
import * as sns from 'aws-cdk-lib/aws-sns';

export interface WorkflowConstructProps {
  /** Lambda invoked for calendar availability lookup */
  readonly calendarLambda: lambda.IFunction;
  /** Lambda invoked for flight-to-window matching */
  readonly matcherLambda: lambda.IFunction;
  /** Lambda invoked to send email notifications */
  readonly notificationLambda: lambda.IFunction;
  /** SNS topic for publishing workflow failure events (Req 10.3) */
  readonly failureTopic?: sns.ITopic;
}

export class WorkflowConstruct extends Construct {
  public readonly stateMachine: sfn.StateMachine;

  constructor(scope: Construct, id: string, props: WorkflowConstructProps) {
    super(scope, id);

    // --- Failure handler ---
    // Captures error details: step name, error type, input payload (Req 10.2)
    const failureHandler = new sfn.Pass(this, 'FailureHandler', {
      parameters: {
        'stepName.$': '$.stepName',
        'errorType.$': '$.error',
        'cause.$': '$.cause',
        'inputPayload.$': '$$.Execution.Input',
      },
    });

    const failState = new sfn.Fail(this, 'WorkflowFailed', {
      error: 'WorkflowFailure',
      cause: 'A workflow step exhausted all retries',
    });

    // Optionally publish failure event to SNS before failing (Req 10.3)
    if (props.failureTopic) {
      const publishFailure = new tasks.SnsPublish(this, 'PublishFailureEvent', {
        topic: props.failureTopic,
        message: sfn.TaskInput.fromJsonPathAt('$'),
        resultPath: sfn.JsonPath.DISCARD,
      });
      failureHandler.next(publishFailure).next(failState);
    } else {
      failureHandler.next(failState);
    }

    // --- CalendarLookup step ---
    // Invokes Calendar Lambda; retry 3x with exponential backoff (Req 7.2, 10.1)
    const calendarLookup = new tasks.LambdaInvoke(this, 'CalendarLookup', {
      lambdaFunction: props.calendarLambda,
      outputPath: '$.Payload',
      retryOnServiceExceptions: false,
    });
    calendarLookup.addRetry({
      errors: ['States.ALL'],
      interval: Duration.seconds(2),
      backoffRate: 2,
      maxAttempts: 3,
    });
    calendarLookup.addCatch(failureHandler, {
      resultPath: '$',
      errors: ['States.ALL'],
    });

    // --- FlightMatching step ---
    // Invokes Matcher Lambda; retry 3x with exponential backoff (Req 10.1)
    const flightMatching = new tasks.LambdaInvoke(this, 'FlightMatching', {
      lambdaFunction: props.matcherLambda,
      outputPath: '$.Payload',
      retryOnServiceExceptions: false,
    });
    flightMatching.addRetry({
      errors: ['States.ALL'],
      interval: Duration.seconds(2),
      backoffRate: 2,
      maxAttempts: 3,
    });
    flightMatching.addCatch(failureHandler, {
      resultPath: '$',
      errors: ['States.ALL'],
    });

    // --- SendNotification step ---
    // Invokes Notification Lambda; retry 2x with exponential backoff (Req 9.3, 10.1)
    const sendNotification = new tasks.LambdaInvoke(this, 'SendNotification', {
      lambdaFunction: props.notificationLambda,
      outputPath: '$.Payload',
      retryOnServiceExceptions: false,
    });
    sendNotification.addRetry({
      errors: ['States.ALL'],
      interval: Duration.seconds(2),
      backoffRate: 2,
      maxAttempts: 2,
    });
    sendNotification.addCatch(failureHandler, {
      resultPath: '$',
      errors: ['States.ALL'],
    });

    // --- Terminal states ---
    const noMatchEnd = new sfn.Succeed(this, 'NoMatchEnd', {
      comment: 'No matching deals found — workflow ends without notification',
    });

    const successEnd = new sfn.Succeed(this, 'SuccessEnd', {
      comment: 'Notification sent successfully',
    });

    // --- Choice: check if matchedDeals is empty (Req 8.4) ---
    const checkMatches = new sfn.Choice(this, 'CheckMatches')
      .when(sfn.Condition.isNotPresent('$.matchedDeals[0]'), noMatchEnd)
      .otherwise(sendNotification.next(successEnd));

    // --- Assemble the workflow chain ---
    // CalendarLookup → FlightMatching → CheckMatches → SendNotification → SuccessEnd
    //                                                → NoMatchEnd
    // Any step catch → FailureHandler → (PublishFailureEvent →) WorkflowFailed
    const definition = calendarLookup.next(flightMatching).next(checkMatches);

    // Standard Workflow state machine (Req 10.1, 10.2, 10.3)
    this.stateMachine = new sfn.StateMachine(this, 'MatchingWorkflow', {
      stateMachineName: 'FlightDealMatchingWorkflow',
      stateMachineType: sfn.StateMachineType.STANDARD,
      definitionBody: sfn.DefinitionBody.fromChainable(definition),
      timeout: Duration.minutes(15),
    });
  }
}
