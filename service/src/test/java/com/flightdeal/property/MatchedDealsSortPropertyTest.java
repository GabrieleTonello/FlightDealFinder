// Feature: flight-deal-notifier, Property 11: Matched deals sorted by price ascending
package com.flightdeal.property;

import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.service.FlightMatcher;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 11: For any list of matched deals, they are sorted by price in non-decreasing order.
 *
 * Validates: Requirements 8.3
 */
class MatchedDealsSortPropertyTest {

    private final FlightMatcher flightMatcher = new FlightMatcher();

    @Property(tries = 100)
    void matchedDealsAreSortedByPriceAscending(
            @ForAll("dealPrices") List<BigDecimal> prices) {

        // Create a wide window that all deals fit within
        TimeWindow window = TimeWindow.builder()
                .startDate("2025-01-01")
                .endDate("2025-12-31")
                .build();

        List<FlightDeal> deals = prices.stream()
                .map(price -> FlightDeal.builder()
                        .destination("TestDest")
                        .price(price)
                        .departureDate("2025-06-01")
                        .returnDate("2025-06-08")
                        .airline("TestAir")
                        .build())
                .collect(Collectors.toList());

        List<FlightDeal> matched = flightMatcher.matchDeals(deals, List.of(window));

        // Verify non-decreasing price order
        for (int i = 1; i < matched.size(); i++) {
            BigDecimal prev = matched.get(i - 1).getPrice();
            BigDecimal curr = matched.get(i).getPrice();
            assertTrue(prev.compareTo(curr) <= 0,
                    "Deals should be sorted by price ascending: " + prev + " <= " + curr);
        }
    }

    @Provide
    Arbitrary<List<BigDecimal>> dealPrices() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("50.00"), new BigDecimal("2000.00"))
                .ofScale(2)
                .list().ofMinSize(1).ofMaxSize(15);
    }
}
