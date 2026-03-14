// Feature: flight-deal-notifier, Property 11: Matched deals sorted by price ascending
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.service.FlightMatcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import net.jqwik.api.*;

/**
 * Property 11: For any list of matched flights, they are sorted by price in non-decreasing order.
 */
class MatchedDealsSortPropertyTest {

  private final FlightMatcher flightMatcher = new FlightMatcher();

  @Property(tries = 100)
  void matchedFlightsAreSortedByPriceAscending(@ForAll("flightPrices") List<Integer> prices) {

    TimeWindow window = TimeWindow.builder().startDate("2025-01-01").endDate("2025-12-31").build();

    List<JsonObject> flights = prices.stream().map(this::createFlight).collect(Collectors.toList());

    List<JsonObject> matched = flightMatcher.matchDeals(flights, List.of(window));

    for (int i = 1; i < matched.size(); i++) {
      int prev = matched.get(i - 1).get("price").getAsInt();
      int curr = matched.get(i).get("price").getAsInt();
      assertTrue(
          prev <= curr, "Flights should be sorted by price ascending: " + prev + " <= " + curr);
    }
  }

  @Provide
  Arbitrary<List<Integer>> flightPrices() {
    return Arbitraries.integers().between(50, 2000).list().ofMinSize(1).ofMaxSize(15);
  }

  private JsonObject createFlight(int price) {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", price);
    flight.addProperty("total_duration", 480);
    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();
    JsonObject dep = new JsonObject();
    dep.addProperty("id", "JFK");
    dep.addProperty("name", "JFK Airport");
    dep.addProperty("time", "2025-06-01 10:00");
    segment.add("departure_airport", dep);
    JsonObject arr = new JsonObject();
    arr.addProperty("id", "CDG");
    arr.addProperty("name", "CDG Airport");
    arr.addProperty("time", "2025-06-08 18:00");
    segment.add("arrival_airport", arr);
    segment.addProperty("airline", "TestAir");
    flights.add(segment);
    flight.add("flights", flights);
    return flight;
  }
}
