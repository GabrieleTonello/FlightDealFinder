import { Construct } from 'constructs';
import { Duration } from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as lambdaEventSources from 'aws-cdk-lib/aws-lambda-event-sources';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as iam from 'aws-cdk-lib/aws-iam';
import { FLIGHT_SEARCH_LAMBDA, WORKFLOW_TRIGGER_LAMBDA } from '../configuration/constants';

export interface ComputeConstructProps {
  readonly table: dynamodb.ITable;
  readonly topic: sns.ITopic;
  readonly queue: sqs.IQueue;
  readonly stage: string;
  readonly stateMachineArn?: string;
}

export class ComputeConstruct extends Construct {
  public readonly flightSearchLambda: lambda.Function;
  public readonly flightSearchAlias: lambda.Alias;
  public readonly workflowTriggerLambda: lambda.Function;

  constructor(scope: Construct, id: string, props: ComputeConstructProps) {
    super(scope, id);

    const codePath = lambda.Code.fromAsset('../service/build/lambda/lambda.zip');

    this.flightSearchLambda = new lambda.Function(this, 'FlightSearchLambda', {
      functionName: `${FLIGHT_SEARCH_LAMBDA.functionName}-${props.stage}`,
      runtime: lambda.Runtime.JAVA_17,
      handler: FLIGHT_SEARCH_LAMBDA.handler,
      code: codePath,
      memorySize: FLIGHT_SEARCH_LAMBDA.memorySize,
      timeout: Duration.seconds(FLIGHT_SEARCH_LAMBDA.timeoutSeconds),
      reservedConcurrentExecutions: FLIGHT_SEARCH_LAMBDA.reservedConcurrency,
      environment: {
        TABLE_NAME: props.table.tableName,
        TOPIC_ARN: props.topic.topicArn,
        SERPAPI_SECRET_NAME: 'serpapi-key',
      },
    });

    props.table.grantWriteData(this.flightSearchLambda);
    props.topic.grantPublish(this.flightSearchLambda);

    // CloudWatch metrics + Secrets Manager read for API key
    this.flightSearchLambda.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ['cloudwatch:PutMetricData'],
        resources: ['*'],
      }),
    );
    this.flightSearchLambda.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ['secretsmanager:GetSecretValue'],
        resources: ['*'],
      }),
    );

    // Create a versioned alias for CodeDeploy traffic shifting
    const version = this.flightSearchLambda.currentVersion;
    this.flightSearchAlias = new lambda.Alias(this, 'FlightSearchLambdaLive', {
      aliasName: 'live',
      version,
    });

    this.workflowTriggerLambda = new lambda.Function(this, 'WorkflowTriggerLambda', {
      functionName: `${WORKFLOW_TRIGGER_LAMBDA.functionName}-${props.stage}`,
      runtime: lambda.Runtime.JAVA_17,
      handler: WORKFLOW_TRIGGER_LAMBDA.handler,
      code: codePath,
      memorySize: WORKFLOW_TRIGGER_LAMBDA.memorySize,
      timeout: Duration.seconds(WORKFLOW_TRIGGER_LAMBDA.timeoutSeconds),
      reservedConcurrentExecutions: WORKFLOW_TRIGGER_LAMBDA.reservedConcurrency,
      environment: {
        STATE_MACHINE_ARN: props.stateMachineArn ?? '',
      },
    });

    if (props.stateMachineArn) {
      this.workflowTriggerLambda.addToRolePolicy(
        new iam.PolicyStatement({
          actions: ['states:StartExecution'],
          resources: [props.stateMachineArn],
        }),
      );
    }

    this.workflowTriggerLambda.addToRolePolicy(
      new iam.PolicyStatement({ actions: ['cloudwatch:PutMetricData'], resources: ['*'] }),
    );

    this.workflowTriggerLambda.addEventSource(new lambdaEventSources.SqsEventSource(props.queue));
  }
}
