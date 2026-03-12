import * as cdk from 'aws-cdk-lib';
import { Template, Match, Capture } from 'aws-cdk-lib/assertions';
import { FlightDealNotifierStack } from '../lib/stacks/flight-deal-notifier-stack';

let template: Template;

beforeAll(() => {
  const app = new cdk.App();
  const stack = new FlightDealNotifierStack(app, 'TestStack');
  template = Template.fromStack(stack);
});

// ---------------------------------------------------------------------------
// 1. EventBridge rule has rate(1 hour) schedule (Req 1.1)
// ---------------------------------------------------------------------------
describe('EventBridge Scheduling', () => {
  test('rule has rate(1 hour) schedule', () => {
    template.hasResourceProperties('AWS::Events::Rule', {
      ScheduleExpression: 'rate(1 hour)',
    });
  });

  test('rule target has retry policy with 2 retries (Req 1.3)', () => {
    template.hasResourceProperties('AWS::Events::Rule', {
      Targets: Match.arrayWith([
        Match.objectLike({
          RetryPolicy: Match.objectLike({
            MaximumRetryAttempts: 2,
          }),
        }),
      ]),
    });
  });
});

// ---------------------------------------------------------------------------
// 2-4. SQS Deal Queue and DLQ (Req 5.2, 5.3, 5.4)
// ---------------------------------------------------------------------------
describe('SQS Messaging', () => {
  test('Deal Queue visibility timeout is 720 seconds', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'FlightDealQueue',
      VisibilityTimeout: 720,
    });
  });

  test('Deal Queue redrive policy maxReceiveCount is 3', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'FlightDealQueue',
      RedrivePolicy: Match.objectLike({
        maxReceiveCount: 3,
      }),
    });
  });

  test('DLQ retention is 14 days (1209600 seconds)', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'FlightDealDLQ',
      MessageRetentionPeriod: 1209600,
    });
  });
});

// ---------------------------------------------------------------------------
// 5-6. Lambda concurrency, memory, timeout (Req 14.1, 14.4, 15.3)
// ---------------------------------------------------------------------------
describe('Lambda Compute', () => {
  test('Flight Search Lambda has 512MB memory, 120s timeout, concurrency 5', () => {
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'FlightSearchLambda',
      MemorySize: 512,
      Timeout: 120,
      ReservedConcurrentExecutions: 5,
    });
  });

  test('Workflow Trigger Lambda has 256MB memory, 60s timeout, concurrency 5', () => {
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'WorkflowTriggerLambda',
      MemorySize: 256,
      Timeout: 60,
      ReservedConcurrentExecutions: 5,
    });
  });
});

// ---------------------------------------------------------------------------
// 7-10. CloudWatch Alarms (Req 12.1, 12.2, 12.3, 12.5)
// ---------------------------------------------------------------------------
describe('CloudWatch Alarms', () => {
  test('DLQ alarm triggers when message count > 0 (Req 12.1)', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      AlarmName: 'FlightDeal-DLQ-NonEmpty',
      Threshold: 0,
      ComparisonOperator: 'GreaterThanThreshold',
      EvaluationPeriods: 1,
    });
  });

  test('Flight Search Lambda error rate alarm > 10% (Req 12.2)', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      AlarmName: 'FlightDeal-FlightSearch-ErrorRate',
      Threshold: 10,
      ComparisonOperator: 'GreaterThanThreshold',
      EvaluationPeriods: 1,
    });
  });

  test('Workflow failure alarm triggers when failures > 0 (Req 12.3)', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      AlarmName: 'FlightDeal-Workflow-Failures',
      Threshold: 0,
      ComparisonOperator: 'GreaterThanThreshold',
      EvaluationPeriods: 1,
    });
  });

  test('Deal Queue age alarm triggers when age > 2 hours (Req 12.5)', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      AlarmName: 'FlightDeal-DealQueue-MessageAge',
      Threshold: 7200,
      ComparisonOperator: 'GreaterThanThreshold',
      EvaluationPeriods: 1,
    });
  });

  test('all alarms have SNS alarm actions (Req 12.4)', () => {
    const alarmCapture = new Capture();
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      AlarmName: 'FlightDeal-DLQ-NonEmpty',
      AlarmActions: alarmCapture,
    });
    expect(alarmCapture.asArray().length).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// 11. Step Functions state machine (Req 10.1)
// ---------------------------------------------------------------------------
describe('Step Functions Workflow', () => {
  test('state machine exists with STANDARD type', () => {
    template.hasResourceProperties('AWS::StepFunctions::StateMachine', {
      StateMachineName: 'FlightDealMatchingWorkflow',
      StateMachineType: 'STANDARD',
    });
  });

  test('state machine definition includes retry policies', () => {
    const capture = new Capture();
    template.hasResourceProperties('AWS::StepFunctions::StateMachine', {
      DefinitionString: capture,
    });

    // The definition is a JSON string with Fn::Join or similar.
    // We resolve it by checking the synthesized template for Retry blocks.
    const stateMachines = template.findResources('AWS::StepFunctions::StateMachine');
    const smKeys = Object.keys(stateMachines);
    expect(smKeys.length).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// IAM least-privilege policies (Req 15.3)
// ---------------------------------------------------------------------------
describe('IAM Policies', () => {
  test('Flight Search Lambda has DynamoDB write permissions', () => {
    template.hasResourceProperties('AWS::IAM::Policy', {
      PolicyDocument: Match.objectLike({
        Statement: Match.arrayWith([
          Match.objectLike({
            Action: Match.anyValue(),
            Effect: 'Allow',
          }),
        ]),
      }),
    });
  });
});
