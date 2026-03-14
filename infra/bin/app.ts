#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { FlightDealPipeline } from '../lib/pipeline/FlightDealPipeline';

const app = new cdk.App();

new FlightDealPipeline(app, 'FlightDealPipeline', {
  githubOwner: 'GabrieleTonello',
  githubRepo: 'flight-deal-notifier',
  githubBranch: 'main',
  env: {
    account: '976193231624',
    region: 'us-east-1',
  },
});
