package com.flightdeal.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Parsed response from the SerpApi Google Flights API. Contains the raw JSON nodes for best_flights
 * and other_flights arrays, plus the raw response body for downstream publishing.
 */
public record FlightSearchResponse(
    List<JsonNode> bestFlights, List<JsonNode> otherFlights, String rawResponse) {
  public int totalFlightCount() {
    return bestFlights.size() + otherFlights.size();
  }

  public boolean hasFlights() {
    return !bestFlights.isEmpty() || !otherFlights.isEmpty();
  }
}
