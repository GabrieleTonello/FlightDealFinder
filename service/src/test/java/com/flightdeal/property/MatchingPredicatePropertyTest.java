// Feature: flight-deal-notifier, Property 10: Flight matching predicate correctness
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.service.FlightMatcher;
import java.time.LocalDate;
import java.util.List;
import net.jqwik.api.*;

/**
 * Property 10: For any flight JsonNode and TimeWindow, the flight matches iff departure date >=
 * window.start AND arrival date <= window.end.
 */
class MatchingPredicatePropertyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final FlightMatcher flightMatcher = new FlightMatcher();

  @Property(tries = 100)
  void flightMatchesIffEntirelyWithinWindow(@ForAll("flightAndWindow") FlightAndWindow input) {

    JsonNode flight = createFlight(input.departureDate, input.arrivalDate);
    TimeWindow window =
        TimeWindow.builder().startDate(input.windowStart).endDate(input.windowEnd).build();

    List<JsonNode> result = flightMatcher.matchDeals(List.of(flight), List.of(window));

    boolean departureOnOrAfterStart = input.departureDate.compareTo(input.windowStart) >= 0;
    boolean arrivalOnOrBeforeEnd = input.arrivalDate.compareTo(input.windowEnd) <= 0;
    boolean shouldMatch = departureOnOrAfterStart && arrivalOnOrBeforeEnd;

    if (shouldMatch) {
      assertFalse(result.isEmpty(), "Flight should match window");
    } else {
      assertTrue(result.isEmpty(), "Flight should NOT match window");
    }
  }

  @Provide
  Arbitrary<FlightAndWindow> flightAndWindow() {
    return Arbitraries.integers()
        .between(0, 300)
        .flatMap(
            windowStartOffset ->
                Arbitraries.integers()
                    .between(5, 60)
                    .flatMap(
                        windowLength ->
                            Arbitraries.integers()
                                .between(-30, 90)
                                .flatMap(
                                    depRelativeToWindow ->
                                        Arbitraries.integers()
                                            .between(3, 40)
                                            .map(
                                                tripLength -> {
                                                  LocalDate base = LocalDate.of(2025, 1, 1);
                                                  LocalDate wStart =
                                                      base.plusDays(windowStartOffset);
                                                  LocalDate wEnd = wStart.plusDays(windowLength);
                                                  LocalDate dep =
                                                      wStart.plusDays(depRelativeToWindow);
                                                  LocalDate arr = dep.plusDays(tripLength);
                                                  return new FlightAndWindow(
                                                      dep.toString(),
                                                      arr.toString(),
                                                      wStart.toString(),
                                                      wEnd.toString());
                                                }))));
  }

  record FlightAndWindow(
      String departureDate, String arrivalDate, String windowStart, String windowEnd) {}

  private static JsonNode createFlight(String depDate, String arrDate) {
    ObjectNode flight = MAPPER.createObjectNode();
    flight.put("price", 200);
    flight.put("total_duration", 480);
    ArrayNode flights = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();
    ObjectNode dep = MAPPER.createObjectNode();
    dep.put("id", "JFK");
    dep.put("name", "JFK Airport");
    dep.put("time", depDate + " 10:00");
    segment.set("departure_airport", dep);
    ObjectNode arr = MAPPER.createObjectNode();
    arr.put("id", "CDG");
    arr.put("name", "CDG Airport");
    arr.put("time", arrDate + " 18:00");
    segment.set("arrival_airport", arr);
    segment.put("airline", "TestAir");
    flights.add(segment);
    flight.set("flights", flights);
    return flight;
  }
}
