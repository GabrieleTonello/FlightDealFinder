// Feature: flight-deal-notifier, Property 10: Flight matching predicate correctness
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.*;

import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.service.FlightMatcher;
import java.time.LocalDate;
import java.util.List;
import net.jqwik.api.*;

/**
 * Property 10: For any FlightDeal and TimeWindow, the flight matches iff departure date >=
 * window.start AND arrival date <= window.end.
 */
class MatchingPredicatePropertyTest {

  private final FlightMatcher flightMatcher = new FlightMatcher();

  @Property(tries = 100)
  void flightMatchesIffEntirelyWithinWindow(@ForAll("flightAndWindow") FlightAndWindow input) {

    FlightDeal deal = createFlight(input.departureDate, input.arrivalDate);
    TimeWindow window =
        TimeWindow.builder().startDate(input.windowStart).endDate(input.windowEnd).build();

    List<FlightDeal> result = flightMatcher.matchDeals(List.of(deal), List.of(window));

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

  private static FlightDeal createFlight(String depDate, String arrDate) {
    return FlightDeal.builder()
        .flights(
            List.of(
                FlightSegment.builder()
                    .departureAirport(
                        Airport.builder()
                            .id("JFK")
                            .name("JFK Airport")
                            .time(depDate + " 10:00")
                            .build())
                    .arrivalAirport(
                        Airport.builder()
                            .id("CDG")
                            .name("CDG Airport")
                            .time(arrDate + " 18:00")
                            .build())
                    .duration(480)
                    .airline("TestAir")
                    .build()))
        .totalDuration(480)
        .price(200)
        .build();
  }
}
