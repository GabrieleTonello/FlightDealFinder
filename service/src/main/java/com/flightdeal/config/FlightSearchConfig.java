package com.flightdeal.config;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Configuration loaded from AWS AppConfig.
 * Maps to the JSON structure in infra/lib/config/*.json.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchConfig {

  private ApiConfig api;
  private SearchConfig search;
  private NotificationConfig notification;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiConfig {
    private String currency;
    private String language;

    @SerializedName("travelClass")
    private int travelClass;

    private int adults;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SearchConfig {
    private List<String> routes;

    @SerializedName("maxPricePerFlight")
    private int maxPricePerFlight;

    @SerializedName("maxStops")
    private int maxStops;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class NotificationConfig {
    @SerializedName("recipientEmail")
    private String recipientEmail;

    @SerializedName("senderEmail")
    private String senderEmail;
  }
}
