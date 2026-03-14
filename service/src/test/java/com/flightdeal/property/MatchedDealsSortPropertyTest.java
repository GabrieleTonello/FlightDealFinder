// Feature: flight-deal-notifier, Property 11: Matched deals sorted by price ascending
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.service.FlightMatcher;
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

    List<FlightDeal> flights = prices.stream().map(this::createFlight).collect(Collectors.toList());

    List<FlightDeal> matched = flightMatcher.matchDeals(flights, List.of(window));

    for (int i = 1; i < matched.size(); i++) {
      int prev = matched.get(i - 1).getPrice();
      int curr = matched.get(i).getPrice();
      assertTrue(
          prev <= curr, "Flights should be sorted by price ascending: " + prev + " <= " + curr);
    }
  }

  @Provide
  Arbitrary<List<Integer>> flightPrices() {
    return Arbitraries.integers().between(50, 2000).list().ofMinSize(1).ofMaxSize(15);
  }

  private FlightDeal createFlight(int price) {
    return FlightDeal.builder()
        .flights(
            List.of(
                FlightSegment.builder()
                    .departureAirport(
                        Airport.builder()
                            .id("JFK")
                            .name("JFK Airport")
                            .time("2025-06-01 10:00")
                            .build())
                    .arrivalAirport(
                        Airport.builder()
                            .id("CDG")
                            .name("CDG Airport")
                            .time("2025-06-08 18:00")
                            .build())
                    .duration(480)
                    .airline("TestAir")
                    .build()))
        .totalDuration(480)
        .price(price)
        .build();
  }
}
