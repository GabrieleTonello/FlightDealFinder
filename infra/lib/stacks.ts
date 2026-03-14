import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { StageConfig } from './pipeline/stageConfig';
import { FlightDealNotifierStack } from './stacks/flight-deal-notifier-stack';

export interface DeploymentEnvironment extends cdk.Environment {
  stageName: string;
}

/**
 * Creates all Flight Deal Notifier stacks for a given deployment stage.
 * Called by the pipeline for each stage (Dev, Prod).
 */
export function createFlightDealStacks(
  scope: Construct,
  env: DeploymentEnvironment,
  config: StageConfig,
): cdk.Stack[] {
  const stacks: cdk.Stack[] = [];

  const mainStack = new FlightDealNotifierStack(
    scope,
    `FlightDealNotifierStack-${config.name}`,
    {
      env: config.env,
      stackName: `flight-deal-notifier-${env.stageName}`,
      description: `Flight Deal Notifier - ${config.name}`,
      stage: env.stageName,
    },
  );
  stacks.push(mainStack);

  return stacks;
}
