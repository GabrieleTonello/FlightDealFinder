package com.flightdeal.service;

import static org.junit.jupiter.api.Assertions.*;

import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
import com.flightdeal.generated.model.TimeWindow;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for FlightMatcher with typed FlightDeal objects. */
class FlightMatcherTest {

  private FlightMatcher flightMatcher;

  @BeforeEach
  void setUp() {
    flightMatcher = new FlightMatcher();
  }

  static FlightDeal flight(int price, String depTime, String arrTime) {
    return FlightDeal.builder()
        .flights(
            List.of(
                FlightSegment.builder()
                    .departureAirport(
                        Airport.builder().id("JFK").name("JFK Airport").time(depTime).build())
                    .arrivalAirport(
                        Airport.builder().id("CDG").name("CDG Airport").time(arrTime).build())
                    .duration(480)
                    .airline("TestAir")
                    .build()))
        .totalDuration(480)
        .price(price)
        .build();
  }

  private TimeWindow window(String start, String end) {
    return TimeWindow.builder().startDate(start).endDate(end).build();
  }

  @Test
  void matchDeals_dealEntirelyWithinWindow_matches() {
    List<FlightDeal> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(1, result.size());
    assertEquals(299, result.get(0).getPrice());
  }

  @Test
  void matchDeals_dealDepartureBeforeWindowStart_rejected() {
    List<FlightDeal> flights = List.of(flight(499, "2025-06-28 10:00", "2025-07-10 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(flights, windows);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_dealReturnAfterWindowEnd_rejected() {
    List<FlightDeal> flights = List.of(flight(350, "2025-07-05 10:00", "2025-07-20 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(flights, windows);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_multipleWindows_matchesDealsInAnyWindow() {
    List<FlightDeal> flights =
        List.of(
            flight(299, "2025-07-05 10:00", "2025-07-10 18:00"),
            flight(599, "2025-08-02 10:00", "2025-08-08 18:00"),
            flight(199, "2025-09-01 10:00", "2025-09-10 18:00"));
    List<TimeWindow> windows =
        List.of(window("2025-07-01", "2025-07-15"), window("2025-08-01", "2025-08-10"));

    List<FlightDeal> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(2, result.size());
    assertEquals(299, result.get(0).getPrice());
    assertEquals(599, result.get(1).getPrice());
  }

  @Test
  void matchDeals_matchedDealsSortedByPriceAscending() {
    List<FlightDeal> flights =
        List.of(
            flight(599, "2025-07-05 10:00", "2025-07-10 18:00"),
            flight(199, "2025-07-03 10:00", "2025-07-08 18:00"),
            flight(399, "2025-07-06 10:00", "2025-07-12 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(3, result.size());
    assertEquals(199, result.get(0).getPrice());
    assertEquals(399, result.get(1).getPrice());
    assertEquals(599, result.get(2).getPrice());
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
    List<FlightDeal> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    assertTrue(flightMatcher.matchDeals(flights, null).isEmpty());
  }

  @Test
  void matchDeals_emptyWindows_returnsEmpty() {
    List<FlightDeal> flights = List.of(flight(299, "2025-07-05 10:00", "2025-07-10 18:00"));
    assertTrue(flightMatcher.matchDeals(flights, Collections.emptyList()).isEmpty());
  }

  @Test
  void matchDeals_flightWithEmptySegments_excluded() {
    FlightDeal deal = FlightDeal.builder().flights(List.of()).totalDuration(480).price(100).build();
    List<FlightDeal> flights = List.of(deal);
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(flights, windows);
    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_exactBoundaryMatch_dealMatchesWindow() {
    List<FlightDeal> flights = List.of(flight(249, "2025-07-01 10:00", "2025-07-15 18:00"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(flights, windows);

    assertEquals(1, result.size());
  }
}
