package com.flightdeal.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import serpapi.SerpApi;
import serpapi.SerpApiException;

/**
 * Proxy client for querying the SerpApi Google Flights API using the official serpapi-java library.
 * Returns raw GSON JsonObjects for best_flights and other_flights.
 */
@Slf4j
public class FlightApiClient {

  private final SerpApi serpApi;

  public FlightApiClient(String apiKey) {
    Map<String, String> auth = new HashMap<>();
    auth.put("api_key", apiKey);
    this.serpApi = new SerpApi(auth);
  }

  /** Constructor for testing — accepts a pre-configured SerpApi instance. */
  public FlightApiClient(SerpApi serpApi) {
    this.serpApi = serpApi;
  }

  /**
   * Searches for flights between two airports on given dates.
   *
   * @param departureId departure airport IATA code
   * @param arrivalId arrival airport IATA code
   * @param outboundDate departure date (YYYY-MM-DD)
   * @param returnDate return date (YYYY-MM-DD)
   * @return parsed response with best and other flights as GSON JsonObjects
   * @throws FlightApiException on API errors
   */
  public FlightSearchResponse searchFlights(
      String departureId, String arrivalId, String outboundDate, String returnDate)
      throws FlightApiException {

    Map<String, String> params = new HashMap<>();
    params.put("engine", "google_flights");
    params.put("departure_id", departureId);
    params.put("arrival_id", arrivalId);
    params.put("outbound_date", outboundDate);
    params.put("return_date", returnDate);
    params.put("currency", "EUR");
    params.put("hl", "en");
    params.put("travel_class", "1");
    params.put("adults", "1");

    String route = departureId + "->" + arrivalId;

    try {
      JsonObject result = serpApi.search(params);

      List<JsonObject> bestFlights = extractFlightArray(result, "best_flights");
      List<JsonObject> otherFlights = extractFlightArray(result, "other_flights");

      log.info(
          "Route {}: {} best flights, {} other flights",
          route,
          bestFlights.size(),
          otherFlights.size());

      return new FlightSearchResponse(bestFlights, otherFlights, result.toString());
    } catch (SerpApiException e) {
      log.error("SerpApi error for route {}: {}", route, e.getMessage(), e);
      throw new FlightApiException(route, "SerpApi error: " + e.getMessage(), "API_ERROR");
    }
  }

  private List<JsonObject> extractFlightArray(JsonObject result, String key) {
    List<JsonObject> flights = new ArrayList<>();
    if (result.has(key) && result.get(key).isJsonArray()) {
      JsonArray array = result.getAsJsonArray(key);
      for (int i = 0; i < array.size(); i++) {
        flights.add(array.get(i).getAsJsonObject());
      }
    }
    return flights;
  }
}
