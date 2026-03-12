// Feature: flight-deal-notifier, Property 10: Flight matching predicate correctness
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.*;

import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.service.FlightMatcher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.jqwik.api.*;

/**
 * Property 10: For any FlightDeal and TimeWindow, the deal matches iff departure >= window.start
 * AND return <= window.end.
 *
 * <p>Validates: Requirements 8.1, 8.2
 */
class MatchingPredicatePropertyTest {

  private final FlightMatcher flightMatcher = new FlightMatcher();

  @Property(tries = 100)
  void dealMatchesIffEntirelyWithinWindow(@ForAll("dealAndWindow") DealAndWindow input) {

    FlightDeal deal =
        FlightDeal.builder()
            .destination("TestDest")
            .price(new BigDecimal("200.00"))
            .departureDate(input.departureDate)
            .returnDate(input.returnDate)
            .airline("TestAir")
            .build();

    TimeWindow window =
        TimeWindow.builder().startDate(input.windowStart).endDate(input.windowEnd).build();

    List<FlightDeal> result = flightMatcher.matchDeals(List.of(deal), List.of(window));

    boolean departureOnOrAfterStart = input.departureDate.compareTo(input.windowStart) >= 0;
    boolean returnOnOrBeforeEnd = input.returnDate.compareTo(input.windowEnd) <= 0;
    boolean shouldMatch = departureOnOrAfterStart && returnOnOrBeforeEnd;

    if (shouldMatch) {
      assertFalse(
          result.isEmpty(),
          "Deal should match: departure="
              + input.departureDate
              + " >= windowStart="
              + input.windowStart
              + " AND return="
              + input.returnDate
              + " <= windowEnd="
              + input.windowEnd);
    } else {
      assertTrue(
          result.isEmpty(),
          "Deal should NOT match: departure="
              + input.departureDate
              + " vs windowStart="
              + input.windowStart
              + ", return="
              + input.returnDate
              + " vs windowEnd="
              + input.windowEnd);
    }
  }

  @Provide
  Arbitrary<DealAndWindow> dealAndWindow() {
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
                                                  LocalDate ret = dep.plusDays(tripLength);
                                                  return new DealAndWindow(
                                                      dep.toString(), ret.toString(),
                                                      wStart.toString(), wEnd.toString());
                                                }))));
  }

  record DealAndWindow(
      String departureDate, String returnDate, String windowStart, String windowEnd) {}
}
