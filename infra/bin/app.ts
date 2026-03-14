#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { FlightDealPipeline } from '../lib/pipeline/FlightDealPipeline';

const app = new cdk.App();

new FlightDealPipeline(app, 'FlightDealPipeline', {
  githubOwner: 'GabrieleTonello',
  githubRepo: 'flight-deal-notifier',
  githubBranch: 'main',
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: 'us-east-1',
  },
});
