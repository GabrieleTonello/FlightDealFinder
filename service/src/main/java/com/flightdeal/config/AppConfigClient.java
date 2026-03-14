package com.flightdeal.config;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Client for fetching configuration from the AWS AppConfig Lambda extension.
 * The extension runs as a sidecar on localhost:2772 and handles caching,
 * polling, and session management automatically.
 */
@Slf4j
@Singleton
public class AppConfigClient {

  private static final String EXTENSION_URL = "http://localhost:2772";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final HttpClient httpClient;
  private final Gson gson;
  private final String applicationId;
  private final String environmentId;
  private final String configProfileId;

  @Inject
  public AppConfigClient() {
    this.httpClient = HttpClient.newHttpClient();
    this.gson = new Gson();
    this.applicationId = System.getenv("APPCONFIG_APPLICATION_ID");
    this.environmentId = System.getenv("APPCONFIG_ENVIRONMENT_ID");
    this.configProfileId = System.getenv("APPCONFIG_CONFIGURATION_PROFILE_ID");
  }

  /**
   * Constructor for testing.
   */
  public AppConfigClient(
      HttpClient httpClient, String applicationId,
      String environmentId, String configProfileId) {
    this.httpClient = httpClient;
    this.gson = new Gson();
    this.applicationId = applicationId;
    this.environmentId = environmentId;
    this.configProfileId = configProfileId;
  }

  /**
   * Fetches the current flight search configuration from AppConfig.
   * The Lambda extension caches the config and handles polling for updates.
   *
   * @return the parsed FlightSearchConfig
   * @throws RuntimeException if the config cannot be fetched or parsed
   */
  public FlightSearchConfig fetchConfig() {
    String url = String.format("%s/applications/%s/environments/%s/configurations/%s",
        EXTENSION_URL, applicationId, environmentId, configProfileId);

    log.info("Fetching config from AppConfig extension: {}", url);

    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(TIMEOUT)
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException("AppConfig returned HTTP " + response.statusCode());
      }

      FlightSearchConfig config = gson.fromJson(response.body(), FlightSearchConfig.class);
      log.info("Loaded config: routes={}, maxPrice={}, currency={}",
          config.getSearch().getRoutes(),
          config.getSearch().getMaxPricePerFlight(),
          config.getApi().getCurrency());

      return config;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.error("Failed to fetch config from AppConfig", e);
      throw new RuntimeException("Failed to fetch AppConfig: " + e.getMessage(), e);
    }
  }
}
