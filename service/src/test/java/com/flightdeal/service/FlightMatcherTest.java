package com.flightdeal.service;

import static org.junit.jupiter.api.Assertions.*;

import com.flightdeal.generated.model.TimeWindow;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for FlightMatcher with JsonObject-based flights from SerpApi. */
class FlightMatcherTest {

  private FlightMatcher flightMatcher;

  @BeforeEach
  void setUp() {
    flightMatcher = new FlightMatcher();
  }

  static JsonObject flight(int price, String depTime, String arrTime) {
    JsonObject node = new JsonObject();
    node.addProperty("price", price);
    node.addProperty("total_duration", 480);

    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();

    JsonObject depAirport = new JsonObject();
    depAirport.addProperty("id", "JFK");
    depAirport.addProperty("name", "JFK Airport");
    depAirport.addProperty("time", depTime);
    segment.add("departure_airport", depAirport);

    JsonObject arrAirport = new JsonObject();
    arrAirport.addProperty("id", "CDG");
    arrAirport.addProperty("name", "CDG Airport");
    arrAirport.addProperty("time", arrTime);
    segment.add("arrival_airport", arrAirport);

    segment.addProperty("airline", "TestAir");
    flights.add(segment);
    node.add("flights", flights);
    return node;
  }

  private TimeWindow window(String start, String end) {
    return TimeWindow.builder().startDate(start).endDate(end).build();
  }

  @Test
  void matchDeals_dealEntirelyWithinWindow_matches() {
    List<JsonObject> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonObject> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(1, result.size());
    assertEquals(299, result.get(0).get("price").getAsInt());
  }

  @Test
  void matchDeals_dealDepartureBeforeWindowStart_rejected() {
    List<JsonObject> flights = List.of(flight(499, "2025-06-28 10:00", "2025-07-10 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonObject> result = flightMatcher.matchDeals(flights, windows);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_dealReturnAfterWindowEnd_rejected() {
    List<JsonObject> flights = List.of(flight(350, "2025-07-05 10:00", "2025-07-20 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonObject> result = flightMatcher.matchDeals(flights, windows);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_multipleWindows_matchesDealsInAnyWindow() {
    List<JsonObject> flights =
        List.of(
            flight(299, "2025-07-05 10:00", "2025-07-10 18:00"),
            flight(599, "2025-08-02 10:00", "2025-08-08 18:00"),
            flight(199, "2025-09-01 10:00", "2025-09-10 18:00"));
    List<TimeWindow> windows =
        List.of(window("2025-07-01", "2025-07-15"), window("2025-08-01", "2025-08-10"));

    List<JsonObject> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(2, result.size());
    // Sorted by price ascending
    assertEquals(299, result.get(0).get("price").getAsInt());
    assertEquals(599, result.get(1).get("price").getAsInt());
  }

  @Test
  void matchDeals_matchedDealsSortedByPriceAscending() {
    List<JsonObject> flights =
        List.of(
            flight(599, "2025-07-05 10:00", "2025-07-10 18:00"),
            flight(199, "2025-07-03 10:00", "2025-07-08 18:00"),
            flight(399, "2025-07-06 10:00", "2025-07-12 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonObject> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(3, result.size());
    assertEquals(199, result.get(0).get("price").getAsInt());
    assertEquals(399, result.get(1).get("price").getAsInt());
    assertEquals(599, result.get(2).get("price").getAsInt());
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
    List<JsonObject> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    assertTrue(flightMatcher.matchDeals(flights, null).isEmpty());
  }

  @Test
  void matchDeals_emptyWindows_returnsEmpty() {
    List<JsonObject> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    assertTrue(flightMatcher.matchDeals(flights, Collections.emptyList()).isEmpty());
  }

  @Test
  void matchDeals_flightWithNoFlightsArray_excluded() {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", 100);
    List<JsonObject> flights = List.of(flight);
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonObject> result = flightMatcher.matchDeals(flights, windows);
    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_flightWithEmptyFlightsArray_excluded() {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", 100);
    flight.add("flights", new JsonArray());
    List<JsonObject> flights = List.of(flight);
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonObject> result = flightMatcher.matchDeals(flights, windows);
    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_exactBoundaryMatch_dealMatchesWindow() {
    List<JsonObject> flights = List.of(flight(249, "2025-07-01 10:00", "2025-07-15 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<JsonObject> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(1, result.size());
  }
}
