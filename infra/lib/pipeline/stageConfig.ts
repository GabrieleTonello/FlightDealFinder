import * as cdk from 'aws-cdk-lib';

export interface StageConfig {
  name: string;
  env: cdk.Environment;
}

export const STAGE_CONFIGS: StageConfig[] = [
  {
    name: 'Dev',
    env: {
      account: process.env.CDK_DEFAULT_ACCOUNT,
      region: 'us-east-1',
    },
  },
  {
    name: 'Prod',
    env: {
      account: process.env.CDK_DEFAULT_ACCOUNT,
      region: 'us-east-1',
    },
  },
];
