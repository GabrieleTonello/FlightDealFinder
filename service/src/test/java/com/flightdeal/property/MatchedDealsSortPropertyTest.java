// Feature: flight-deal-notifier, Property 11: Matched deals sorted by price ascending
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.service.FlightMatcher;
import java.util.List;
import java.util.stream.Collectors;
import net.jqwik.api.*;

/**
 * Property 11: For any list of matched flights, they are sorted by price in non-decreasing order.
 */
class MatchedDealsSortPropertyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final FlightMatcher flightMatcher = new FlightMatcher();

  @Property(tries = 100)
  void matchedFlightsAreSortedByPriceAscending(@ForAll("flightPrices") List<Integer> prices) {

    TimeWindow window = TimeWindow.builder().startDate("2025-01-01").endDate("2025-12-31").build();

    List<JsonNode> flights = prices.stream().map(this::createFlight).collect(Collectors.toList());

    List<JsonNode> matched = flightMatcher.matchDeals(flights, List.of(window));

    for (int i = 1; i < matched.size(); i++) {
      int prev = matched.get(i - 1).path("price").asInt();
      int curr = matched.get(i).path("price").asInt();
      assertTrue(
          prev <= curr, "Flights should be sorted by price ascending: " + prev + " <= " + curr);
    }
  }

  @Provide
  Arbitrary<List<Integer>> flightPrices() {
    return Arbitraries.integers().between(50, 2000).list().ofMinSize(1).ofMaxSize(15);
  }

  private JsonNode createFlight(int price) {
    ObjectNode flight = MAPPER.createObjectNode();
    flight.put("price", price);
    flight.put("total_duration", 480);
    ArrayNode flights = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();
    ObjectNode dep = MAPPER.createObjectNode();
    dep.put("id", "JFK");
    dep.put("name", "JFK Airport");
    dep.put("time", "2025-06-01 10:00");
    segment.set("departure_airport", dep);
    ObjectNode arr = MAPPER.createObjectNode();
    arr.put("id", "CDG");
    arr.put("name", "CDG Airport");
    arr.put("time", "2025-06-08 18:00");
    segment.set("arrival_airport", arr);
    segment.put("airline", "TestAir");
    flights.add(segment);
    flight.set("flights", flights);
    return flight;
  }
}
