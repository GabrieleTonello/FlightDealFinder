#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { FlightDealNotifierStack } from '../lib/stacks/flight-deal-notifier-stack';

const app = new cdk.App();

new FlightDealNotifierStack(app, 'FlightDealNotifierStack', {
  description: 'Flight Deal Notifier infrastructure stack',
});
