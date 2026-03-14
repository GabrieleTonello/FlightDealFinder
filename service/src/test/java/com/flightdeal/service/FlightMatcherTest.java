package com.flightdeal.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.generated.model.TimeWindow;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for FlightMatcher with JsonNode-based flights from SerpApi. */
class FlightMatcherTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private FlightMatcher flightMatcher;

  @BeforeEach
  void setUp() {
    flightMatcher = new FlightMatcher();
  }

  static JsonNode flight(int price, String depTime, String arrTime) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("price", price);
    node.put("total_duration", 480);

    ArrayNode flights = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();

    ObjectNode depAirport = MAPPER.createObjectNode();
    depAirport.put("id", "JFK");
    depAirport.put("name", "JFK Airport");
    depAirport.put("time", depTime);
    segment.set("departure_airport", depAirport);

    ObjectNode arrAirport = MAPPER.createObjectNode();
    arrAirport.put("id", "CDG");
    arrAirport.put("name", "CDG Airport");
    arrAirport.put("time", arrTime);
    segment.set("arrival_airport", arrAirport);

    segment.put("airline", "TestAir");
    flights.add(segment);
    node.set("flights", flights);
    return node;
  }

  private TimeWindow window(String start, String end) {
    return TimeWindow.builder().startDate(start).endDate(end).build();
  }

  @Test
  void matchDeals_dealEntirelyWithinWindow_matches() {
    List<JsonNode> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonNode> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(1, result.size());
    assertEquals(299, result.get(0).path("price").asInt());
  }

  @Test
  void matchDeals_dealDepartureBeforeWindowStart_rejected() {
    List<JsonNode> flights = List.of(flight(499, "2025-06-28 10:00", "2025-07-10 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonNode> result = flightMatcher.matchDeals(flights, windows);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_dealReturnAfterWindowEnd_rejected() {
    List<JsonNode> flights = List.of(flight(350, "2025-07-05 10:00", "2025-07-20 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonNode> result = flightMatcher.matchDeals(flights, windows);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_multipleWindows_matchesDealsInAnyWindow() {
    List<JsonNode> flights =
        List.of(
            flight(299, "2025-07-05 10:00", "2025-07-10 18:00"),
            flight(599, "2025-08-02 10:00", "2025-08-08 18:00"),
            flight(199, "2025-09-01 10:00", "2025-09-10 18:00"));
    List<TimeWindow> windows =
        List.of(window("2025-07-01", "2025-07-15"), window("2025-08-01", "2025-08-10"));

    List<JsonNode> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(2, result.size());
    // Sorted by price ascending
    assertEquals(299, result.get(0).path("price").asInt());
    assertEquals(599, result.get(1).path("price").asInt());
  }

  @Test
  void matchDeals_matchedDealsSortedByPriceAscending() {
    List<JsonNode> flights =
        List.of(
            flight(599, "2025-07-05 10:00", "2025-07-10 18:00"),
            flight(199, "2025-07-03 10:00", "2025-07-08 18:00"),
            flight(399, "2025-07-06 10:00", "2025-07-12 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonNode> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(3, result.size());
    assertEquals(199, result.get(0).path("price").asInt());
    assertEquals(399, result.get(1).path("price").asInt());
    assertEquals(599, result.get(2).path("price").asInt());
  }

  @Test
  void matchDeals_nullFlights_returnsEmpty() {
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));
    assertTrue(flightMatcher.matchDeals(null, windows).isEmpty());
  }

  @Test
  void matchDeals_emptyFlights_returnsEmpty() {
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));
    assertTrue(flightMatcher.matchDeals(Collections.emptyList(), windows).isEmpty());
  }

  @Test
  void matchDeals_nullWindows_returnsEmpty() {
    List<JsonNode> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    assertTrue(flightMatcher.matchDeals(flights, null).isEmpty());
  }

  @Test
  void matchDeals_emptyWindows_returnsEmpty() {
    List<JsonNode> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    assertTrue(flightMatcher.matchDeals(flights, Collections.emptyList()).isEmpty());
  }

  @Test
  void matchDeals_exactBoundaryMatch_dealMatchesWindow() {
    List<JsonNode> flights = List.of(flight(249, "2025-07-01 10:00", "2025-07-15 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonNode> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(1, result.size());
  }
}
