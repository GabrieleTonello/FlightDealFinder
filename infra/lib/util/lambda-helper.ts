import { Duration } from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

export interface JavaLambdaConfig {
  readonly functionName: string;
  readonly handler: string;
  readonly memorySize: number;
  readonly timeoutSeconds: number;
  readonly reservedConcurrency: number;
  readonly environment?: Record<string, string>;
}

const SERVICE_CODE_PATH = '../service/build/libs/';

/**
 * Creates a Java 17 Lambda function with consistent defaults.
 * Automatically adds CloudWatch PutMetricData permission for custom metrics.
 */
export function createJavaLambda(
  scope: Construct,
  id: string,
  config: JavaLambdaConfig,
): lambda.Function {
  const fn = new lambda.Function(scope, id, {
    functionName: config.functionName,
    runtime: lambda.Runtime.JAVA_17,
    handler: config.handler,
    code: lambda.Code.fromAsset(SERVICE_CODE_PATH),
    memorySize: config.memorySize,
    timeout: Duration.seconds(config.timeoutSeconds),
    reservedConcurrentExecutions: config.reservedConcurrency,
    environment: config.environment ?? {},
  });

  // All Lambdas need CloudWatch PutMetricData for custom metrics
  fn.addToRolePolicy(
    new iam.PolicyStatement({
      actions: ['cloudwatch:PutMetricData'],
      resources: ['*'],
    }),
  );

  return fn;
}
