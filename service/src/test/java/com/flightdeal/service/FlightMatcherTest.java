package com.flightdeal.service;

import static org.junit.jupiter.api.Assertions.*;

import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.TimeWindow;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FlightMatcher (pure logic, no mocks needed). Validates: Requirements 8.1, 8.2,
 * 8.3, 8.4, 17.2
 */
class FlightMatcherTest {

  private FlightMatcher flightMatcher;

  @BeforeEach
  void setUp() {
    flightMatcher = new FlightMatcher();
  }

  private FlightDeal deal(String destination, String price, String dep, String ret) {
    return FlightDeal.builder()
        .destination(destination)
        .price(new BigDecimal(price))
        .departureDate(dep)
        .returnDate(ret)
        .airline("TestAir")
        .build();
  }

  private TimeWindow window(String start, String end) {
    return TimeWindow.builder().startDate(start).endDate(end).build();
  }

  // ---- Deal entirely within window matches ----

  @Test
  void matchDeals_dealEntirelyWithinWindow_matches() {
    List<FlightDeal> deals = List.of(deal("Paris", "299.99", "2025-07-05", "2025-07-10"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, windows);

    assertEquals(1, result.size());
    assertEquals("Paris", result.get(0).getDestination());
  }

  // ---- Deal partially overlapping rejected ----

  @Test
  void matchDeals_dealDepartureBeforeWindowStart_rejected() {
    List<FlightDeal> deals = List.of(deal("Tokyo", "499.99", "2025-06-28", "2025-07-10"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, windows);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_dealReturnAfterWindowEnd_rejected() {
    List<FlightDeal> deals = List.of(deal("London", "350.00", "2025-07-05", "2025-07-20"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, windows);

    assertTrue(result.isEmpty());
  }

  // ---- Deal outside window rejected ----

  @Test
  void matchDeals_dealCompletelyOutsideWindow_rejected() {
    List<FlightDeal> deals = List.of(deal("Berlin", "199.99", "2025-08-01", "2025-08-10"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, windows);

    assertTrue(result.isEmpty());
  }

  // ---- Multiple windows with matches ----

  @Test
  void matchDeals_multipleWindows_matchesDealsInAnyWindow() {
    List<FlightDeal> deals =
        List.of(
            deal("Paris", "299.99", "2025-07-05", "2025-07-10"),
            deal("Tokyo", "599.99", "2025-08-02", "2025-08-08"),
            deal("Berlin", "199.99", "2025-09-01", "2025-09-10"));
    List<TimeWindow> windows =
        List.of(window("2025-07-01", "2025-07-15"), window("2025-08-01", "2025-08-10"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, windows);

    assertEquals(2, result.size());
    // Sorted by price ascending: Paris (299.99), Tokyo (599.99)
    assertEquals("Paris", result.get(0).getDestination());
    assertEquals("Tokyo", result.get(1).getDestination());
  }

  // ---- Matched deals sorted by price ascending ----

  @Test
  void matchDeals_matchedDealsSortedByPriceAscending() {
    List<FlightDeal> deals =
        List.of(
            deal("Tokyo", "599.99", "2025-07-05", "2025-07-10"),
            deal("Paris", "199.99", "2025-07-03", "2025-07-08"),
            deal("London", "399.99", "2025-07-06", "2025-07-12"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, windows);

    assertEquals(3, result.size());
    assertEquals(new BigDecimal("199.99"), result.get(0).getPrice());
    assertEquals(new BigDecimal("399.99"), result.get(1).getPrice());
    assertEquals(new BigDecimal("599.99"), result.get(2).getPrice());
  }

  // ---- Null/empty deals returns empty ----

  @Test
  void matchDeals_nullDeals_returnsEmpty() {
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(null, windows);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_emptyDeals_returnsEmpty() {
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(Collections.emptyList(), windows);

    assertTrue(result.isEmpty());
  }

  // ---- Null/empty windows returns empty ----

  @Test
  void matchDeals_nullWindows_returnsEmpty() {
    List<FlightDeal> deals = List.of(deal("Paris", "299.99", "2025-07-05", "2025-07-10"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, null);

    assertTrue(result.isEmpty());
  }

  @Test
  void matchDeals_emptyWindows_returnsEmpty() {
    List<FlightDeal> deals = List.of(deal("Paris", "299.99", "2025-07-05", "2025-07-10"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, Collections.emptyList());

    assertTrue(result.isEmpty());
  }

  // ---- Single deal single window exact boundary match ----

  @Test
  void matchDeals_exactBoundaryMatch_dealMatchesWindow() {
    List<FlightDeal> deals = List.of(deal("Rome", "249.99", "2025-07-01", "2025-07-15"));
    List<TimeWindow> windows = List.of(window("2025-07-01", "2025-07-15"));

    List<FlightDeal> result = flightMatcher.matchDeals(deals, windows);

    assertEquals(1, result.size());
    assertEquals("Rome", result.get(0).getDestination());
  }
}
