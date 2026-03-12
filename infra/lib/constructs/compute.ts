import { Construct } from 'constructs';
import { Duration } from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as lambdaEventSources from 'aws-cdk-lib/aws-lambda-event-sources';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as iam from 'aws-cdk-lib/aws-iam';

export interface ComputeConstructProps {
  /** DynamoDB Price Store table for granting write access */
  readonly table: dynamodb.ITable;
  /** SNS Deal Topic for granting publish access */
  readonly topic: sns.ITopic;
  /** SQS Deal Queue for event source mapping and consume access */
  readonly queue: sqs.IQueue;
  /** Step Functions state machine ARN (optional, set after workflow construct is created) */
  readonly stateMachineArn?: string;
}

export class ComputeConstruct extends Construct {
  public readonly flightSearchLambda: lambda.Function;
  public readonly workflowTriggerLambda: lambda.Function;

  constructor(scope: Construct, id: string, props: ComputeConstructProps) {
    super(scope, id);

    // Flight Search Lambda — Java 17, 512 MB, 120s timeout, reserved concurrency 5
    // (Req 14.1, 14.4, 15.1, 15.2, 20.1)
    this.flightSearchLambda = new lambda.Function(this, 'FlightSearchLambda', {
      functionName: 'FlightSearchLambda',
      runtime: lambda.Runtime.JAVA_17,
      handler: 'com.flightdeal.handler.FlightSearchHandler',
      code: lambda.Code.fromAsset('../service/build/libs/'),
      memorySize: 512,
      timeout: Duration.seconds(120),
      reservedConcurrentExecutions: 5,
      environment: {
        TABLE_NAME: props.table.tableName,
        TOPIC_ARN: props.topic.topicArn,
        DESTINATIONS: '',
        FLIGHT_API_BASE_URL: '',
        FLIGHT_API_KEY: '',
      },
    });

    // IAM: DynamoDB write access to the Price Store table (least-privilege) (Req 15.3)
    props.table.grantWriteData(this.flightSearchLambda);

    // IAM: SNS publish access to the Deal Topic (least-privilege) (Req 15.3)
    props.topic.grantPublish(this.flightSearchLambda);

    // IAM: CloudWatch PutMetricData for custom metrics (Req 11.1)
    this.flightSearchLambda.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ['cloudwatch:PutMetricData'],
        resources: ['*'],
      }),
    );

    // Workflow Trigger Lambda — Java 17, 256 MB, 60s timeout, reserved concurrency 5
    // (Req 14.1, 14.4, 15.1, 15.2, 20.1)
    this.workflowTriggerLambda = new lambda.Function(this, 'WorkflowTriggerLambda', {
      functionName: 'WorkflowTriggerLambda',
      runtime: lambda.Runtime.JAVA_17,
      handler: 'com.flightdeal.handler.WorkflowTriggerHandler',
      code: lambda.Code.fromAsset('../service/build/libs/'),
      memorySize: 256,
      timeout: Duration.seconds(60),
      reservedConcurrentExecutions: 5,
      environment: {
        STATE_MACHINE_ARN: props.stateMachineArn ?? '',
      },
    });

    // IAM: Step Functions StartExecution (least-privilege) (Req 15.3)
    if (props.stateMachineArn) {
      this.workflowTriggerLambda.addToRolePolicy(
        new iam.PolicyStatement({
          actions: ['states:StartExecution'],
          resources: [props.stateMachineArn],
        }),
      );
    }

    // IAM: CloudWatch PutMetricData for custom metrics (Req 11.2)
    this.workflowTriggerLambda.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ['cloudwatch:PutMetricData'],
        resources: ['*'],
      }),
    );

    // SQS event source mapping — Workflow Trigger Lambda consumes from Deal Queue (Req 6.1)
    this.workflowTriggerLambda.addEventSource(new lambdaEventSources.SqsEventSource(props.queue));
  }
}
