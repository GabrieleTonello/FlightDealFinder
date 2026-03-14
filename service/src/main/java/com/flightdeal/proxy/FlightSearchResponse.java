package com.flightdeal.proxy;

import com.flightdeal.generated.model.FlightDeal;
import java.util.List;

/**
 * Parsed response from the SerpApi Google Flights API. Contains typed {@link FlightDeal} objects
 * for best_flights and other_flights arrays, plus the raw response string for downstream
 * publishing.
 */
public record FlightSearchResponse(
    List<FlightDeal> bestFlights, List<FlightDeal> otherFlights, String rawResponse) {
  public int totalFlightCount() {
    return bestFlights.size() + otherFlights.size();
  }

  public boolean hasFlights() {
    return !bestFlights.isEmpty() || !otherFlights.isEmpty();
  }
}
