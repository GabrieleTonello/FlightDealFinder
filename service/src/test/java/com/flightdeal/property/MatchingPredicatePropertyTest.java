// Feature: flight-deal-notifier, Property 10: Flight matching predicate correctness
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.*;

import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.service.FlightMatcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.util.List;
import net.jqwik.api.*;

/**
 * Property 10: For any flight JsonObject and TimeWindow, the flight matches iff departure date >=
 * window.start AND arrival date <= window.end.
 */
class MatchingPredicatePropertyTest {

  private final FlightMatcher flightMatcher = new FlightMatcher();

  @Property(tries = 100)
  void flightMatchesIffEntirelyWithinWindow(@ForAll("flightAndWindow") FlightAndWindow input) {

    JsonObject flight = createFlight(input.departureDate, input.arrivalDate);
    TimeWindow window =
        TimeWindow.builder().startDate(input.windowStart).endDate(input.windowEnd).build();

    List<JsonObject> result = flightMatcher.matchDeals(List.of(flight), List.of(window));

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

  private static JsonObject createFlight(String depDate, String arrDate) {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", 200);
    flight.addProperty("total_duration", 480);
    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();
    JsonObject dep = new JsonObject();
    dep.addProperty("id", "JFK");
    dep.addProperty("name", "JFK Airport");
    dep.addProperty("time", depDate + " 10:00");
    segment.add("departure_airport", dep);
    JsonObject arr = new JsonObject();
    arr.addProperty("id", "CDG");
    arr.addProperty("name", "CDG Airport");
    arr.addProperty("time", arrDate + " 18:00");
    segment.add("arrival_airport", arr);
    segment.addProperty("airline", "TestAir");
    flights.add(segment);
    flight.add("flights", flights);
    return flight;
  }
}
