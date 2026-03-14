import * as cdk from 'aws-cdk-lib';

/**
 * AWS account ID is not considered sensitive by AWS.
 * It's safe to hardcode here for pipeline stage configuration.
 * See: https://docs.aws.amazon.com/accounts/latest/reference/manage-acct-identifiers.html
 */
const ACCOUNT_ID = '976193231624';

export interface StageConfig {
  name: string;
  env: cdk.Environment;
}

export const STAGE_CONFIGS: StageConfig[] = [
  {
    name: 'Dev',
    env: {
      account: ACCOUNT_ID,
      region: 'eu-west-1',
    },
  },
  {
    name: 'Prod',
    env: {
      account: ACCOUNT_ID,
      region: 'us-east-1',
    },
  },
];
