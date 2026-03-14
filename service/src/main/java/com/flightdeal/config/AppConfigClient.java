package com.flightdeal.config;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;

/**
 * Client for fetching configuration from AWS AppConfig using the SDK directly. Starts a
 * configuration session and retrieves the latest config.
 */
@Slf4j
@Singleton
public class AppConfigClient {

  private final Gson gson;
  private final String applicationId;
  private final String environmentId;
  private final String configProfileId;

  @Inject
  public AppConfigClient() {
    this.gson = new Gson();
    this.applicationId = System.getenv("APPCONFIG_APPLICATION_ID");
    this.environmentId = System.getenv("APPCONFIG_ENVIRONMENT_ID");
    this.configProfileId = System.getenv("APPCONFIG_CONFIGURATION_PROFILE_ID");
  }

  /** Constructor for testing. */
  public AppConfigClient(String applicationId, String environmentId, String configProfileId) {
    this.gson = new Gson();
    this.applicationId = applicationId;
    this.environmentId = environmentId;
    this.configProfileId = configProfileId;
  }

  /**
   * Fetches the current flight search configuration from AppConfig. Falls back to default config if
   * AppConfig is unavailable.
   */
  public FlightSearchConfig fetchConfig() {
    if (applicationId == null || applicationId.isBlank()) {
      log.warn("AppConfig not configured, using default config");
      return defaultConfig();
    }

    try (AppConfigDataClient client = AppConfigDataClient.create()) {
      StartConfigurationSessionResponse session =
          client.startConfigurationSession(
              StartConfigurationSessionRequest.builder()
                  .applicationIdentifier(applicationId)
                  .environmentIdentifier(environmentId)
                  .configurationProfileIdentifier(configProfileId)
                  .build());

      GetLatestConfigurationResponse configResponse =
          client.getLatestConfiguration(
              GetLatestConfigurationRequest.builder()
                  .configurationToken(session.initialConfigurationToken())
                  .build());

      String configJson = configResponse.configuration().asUtf8String();
      log.info("Loaded config from AppConfig: {}", configJson);

      return gson.fromJson(configJson, FlightSearchConfig.class);
    } catch (Exception e) {
      log.error("Failed to fetch config from AppConfig, using defaults", e);
      return defaultConfig();
    }
  }

  private FlightSearchConfig defaultConfig() {
    return FlightSearchConfig.builder()
        .api(
            FlightSearchConfig.ApiConfig.builder()
                .currency("EUR")
                .language("en")
                .travelClass(1)
                .adults(1)
                .build())
        .search(
            FlightSearchConfig.SearchConfig.builder()
                .routes(java.util.List.of("JFK-CDG"))
                .maxPricePerFlight(1000)
                .maxStops(2)
                .build())
        .notification(
            FlightSearchConfig.NotificationConfig.builder()
                .recipientEmail("")
                .senderEmail("")
                .build())
        .build();
  }
}
