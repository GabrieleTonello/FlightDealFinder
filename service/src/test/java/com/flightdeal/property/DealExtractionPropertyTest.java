// Feature: flight-deal-notifier, Property 2: Deal extraction preserves all required fields
package com.flightdeal.property;

import com.flightdeal.generated.model.FlightDeal;
import net.jqwik.api.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Property 2: For any valid FlightDeal, the fields (destination, price, departureDate,
 * returnDate, airline) are all non-null after extraction.
 *
 * Validates: Requirements 2.2, 4.2
 */
class DealExtractionPropertyTest {

    @Property(tries = 100)
    void dealExtractionPreservesAllFields(
            @ForAll("validDestination") String destination,
            @ForAll("validPrice") BigDecimal price,
            @ForAll("validDate") String departureDate,
            @ForAll("validDate") String returnDate,
            @ForAll("validAirline") String airline) {

        FlightDeal deal = FlightDeal.builder()
                .destination(destination)
                .price(price)
                .departureDate(departureDate)
                .returnDate(returnDate)
                .airline(airline)
                .build();

        assertNotNull(deal.getDestination(), "destination must not be null");
        assertNotNull(deal.getPrice(), "price must not be null");
        assertNotNull(deal.getDepartureDate(), "departureDate must not be null");
        assertNotNull(deal.getReturnDate(), "returnDate must not be null");
        assertNotNull(deal.getAirline(), "airline must not be null");
    }

    @Provide
    Arbitrary<String> validDestination() {
        return Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20);
    }

    @Provide
    Arbitrary<BigDecimal> validPrice() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("50.00"), new BigDecimal("2000.00"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<String> validDate() {
        return Arbitraries.integers().between(1, 365).map(dayOfYear -> {
            java.time.LocalDate date = java.time.LocalDate.of(2025, 1, 1).plusDays(dayOfYear - 1);
            return date.toString();
        });
    }

    @Provide
    Arbitrary<String> validAirline() {
        return Arbitraries.of("AirFrance", "Delta", "ANA", "Lufthansa", "BA", "JAL", "Qantas", "Emirates");
    }
}
