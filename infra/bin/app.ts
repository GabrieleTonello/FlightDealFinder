#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';

const app = new cdk.App();

// Placeholder — constructs will be composed into a stack in Task 12.1
new cdk.Stack(app, 'FlightDealNotifierStack', {
  description: 'Flight Deal Notifier infrastructure stack',
});
