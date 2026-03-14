import * as cdk from 'aws-cdk-lib';
import { readFileSync, existsSync } from 'fs';

// Load account ID from .env file or environment
function loadAccountId(): string {
  const envPath = '.env';
  if (existsSync(envPath)) {
    const content = readFileSync(envPath, 'utf-8');
    const match = content.match(/AWS_ACCOUNT_ID=(\d+)/);
    if (match) {
      return match[1];
    }
  }
  return process.env.CDK_DEFAULT_ACCOUNT || process.env.AWS_ACCOUNT_ID || '';
}

const ACCOUNT_ID = loadAccountId();

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
