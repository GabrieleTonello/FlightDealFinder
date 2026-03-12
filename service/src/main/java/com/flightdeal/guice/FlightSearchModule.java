package com.flightdeal.guice;

import com.flightdeal.dao.DynamoDbPriceRecordDao;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Guice module for the Flight Search Lambda handler. Binds all dependencies needed by
 * FlightSearchHandler.
 */
public class FlightSearchModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(PriceRecordDao.class).to(DynamoDbPriceRecordDao.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  FlightApiClient provideFlightApiClient() {
    String baseUrl = System.getenv("FLIGHT_API_BASE_URL");
    String apiKey = System.getenv("FLIGHT_API_KEY");
    return new FlightApiClient(HttpClient.newHttpClient(), baseUrl, apiKey, Duration.ofSeconds(30));
  }

  @Provides
  @Singleton
  DynamoDbClient provideDynamoDbClient() {
    return DynamoDbClient.create();
  }

  @Provides
  @Singleton
  DynamoDbEnhancedClient provideDynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
    return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
  }

  @Provides
  @Singleton
  SnsClient provideSnsClient() {
    return SnsClient.create();
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
  @Named("TOPIC_ARN")
  String provideTopicArn() {
    return System.getenv("TOPIC_ARN");
  }

  @Provides
  @Named("DESTINATIONS")
  List<String> provideDestinations() {
    String destinationsEnv = System.getenv("DESTINATIONS");
    if (destinationsEnv == null || destinationsEnv.isBlank()) {
      return List.of();
    }
    return Arrays.asList(destinationsEnv.split(","));
  }
}
