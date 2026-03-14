package com.flightdeal.proxy;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * Parsed response from the SerpApi Google Flights API. Contains GSON JsonObjects for best_flights
 * and other_flights arrays, plus the raw response string for downstream publishing.
 */
public record FlightSearchResponse(
    List<JsonObject> bestFlights, List<JsonObject> otherFlights, String rawResponse) {
  public int totalFlightCount() {
    return bestFlights.size() + otherFlights.size();
  }

  public boolean hasFlights() {
    return !bestFlights.isEmpty() || !otherFlights.isEmpty();
  }
}
