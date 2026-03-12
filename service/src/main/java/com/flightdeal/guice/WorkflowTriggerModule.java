package com.flightdeal.guice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightdeal.metrics.MetricsEmitter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.sfn.SfnClient;

/**
 * Guice module for the Workflow Trigger Lambda handler. Binds all dependencies needed by
 * WorkflowTriggerHandler.
 */
public class WorkflowTriggerModule extends AbstractModule {

  @Provides
  @Singleton
  SfnClient provideSfnClient() {
    return SfnClient.create();
  }

  @Provides
  @Singleton
  CloudWatchClient provideCloudWatchClient() {
    return CloudWatchClient.create();
  }

  @Provides
  @Singleton
  MetricsEmitter provideMetricsEmitter(CloudWatchClient cloudWatchClient) {
    return new MetricsEmitter(cloudWatchClient);
  }

  @Provides
  @Singleton
  ObjectMapper provideObjectMapper() {
    return new ObjectMapper();
  }

  @Provides
  @Named("STATE_MACHINE_ARN")
  String provideStateMachineArn() {
    return System.getenv("STATE_MACHINE_ARN");
  }
}
