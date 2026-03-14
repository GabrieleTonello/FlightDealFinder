package com.flightdeal.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy client for querying the SerpApi Google Flights API. Parses best_flights and other_flights
 * from the response.
 */
@Slf4j
public class FlightApiClient {

  private final HttpClient httpClient;
  private final String apiKey;
  private final Duration timeout;
  private final ObjectMapper objectMapper;

  public FlightApiClient(HttpClient httpClient, String apiKey, Duration timeout) {
    this.httpClient = httpClient;
    this.apiKey = apiKey;
    this.timeout = timeout;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Searches for flights between two airports on given dates.
   *
   * @param departureId departure airport IATA code
   * @param arrivalId arrival airport IATA code
   * @param outboundDate departure date (YYYY-MM-DD)
   * @param returnDate return date (YYYY-MM-DD)
   * @return parsed API response with best and other flights
   * @throws FlightApiException on HTTP errors, timeouts, or parse failures
   */
  public FlightSearchResponse searchFlights(
      String departureId, String arrivalId, String outboundDate, String returnDate)
      throws FlightApiException {

    String url =
        String.format(
            "https://serpapi.com/search?engine=google_flights"
                + "&departure_id=%s&arrival_id=%s"
                + "&outbound_date=%s&return_date=%s"
                + "&currency=EUR&hl=en&travel_class=1&adults=1"
                + "&api_key=%s",
            departureId, arrivalId, outboundDate, returnDate, apiKey);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .timeout(timeout)
            .GET()
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new FlightApiException(
            departureId + "->" + arrivalId,
            "API returned HTTP " + response.statusCode(),
            "HTTP_ERROR");
      }

      return parseResponse(response.body());
    } catch (java.net.http.HttpTimeoutException e) {
      throw new FlightApiException(
          departureId + "->" + arrivalId, "Request timed out: " + e.getMessage(), "TIMEOUT");
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new FlightApiException(
          departureId + "->" + arrivalId, "Request failed: " + e.getMessage(), "IO_ERROR");
    }
  }

  private FlightSearchResponse parseResponse(String responseBody) throws FlightApiException {
    try {
      JsonNode root = objectMapper.readTree(responseBody);

      List<JsonNode> bestFlights = new ArrayList<>();
      if (root.has("best_flights") && root.get("best_flights").isArray()) {
        root.get("best_flights").forEach(bestFlights::add);
      }

      List<JsonNode> otherFlights = new ArrayList<>();
      if (root.has("other_flights") && root.get("other_flights").isArray()) {
        root.get("other_flights").forEach(otherFlights::add);
      }

      log.info(
          "Parsed {} best flights and {} other flights", bestFlights.size(), otherFlights.size());
      return new FlightSearchResponse(bestFlights, otherFlights, responseBody);
    } catch (Exception e) {
      throw new FlightApiException(
          "unknown", "Failed to parse response: " + e.getMessage(), "PARSE_ERROR");
    }
  }
}
