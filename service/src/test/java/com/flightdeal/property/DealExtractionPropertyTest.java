// Feature: flight-deal-notifier, Property 2: Deal extraction preserves all required fields
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flightdeal.config.FlightSearchConfig;
import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.CarbonEmissions;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.proxy.FlightSearchResponse;
import java.util.List;
import net.jqwik.api.*;

/**
 * Property 2: For any valid FlightDeal, the parsed PriceRecordEntity has all required fields
 * non-null.
 */
class DealExtractionPropertyTest {

  @Property(tries = 100)
  void dealExtractionPreservesAllFields(
      @ForAll("validPrice") int price,
      @ForAll("validAirline") String airline,
      @ForAll("validAirportId") String depId,
      @ForAll("validAirportId") String arrId) {

    FlightDeal deal = createFlight(price, airline, depId, arrId);
    FlightSearchResponse response = new FlightSearchResponse(List.of(deal), List.of(), "{}");

    FlightSearchHandler handler =
        new FlightSearchHandler(
            null,
            null,
            null,
            null,
            null,
            FlightSearchConfig.builder()
                .api(
                    FlightSearchConfig.ApiConfig.builder()
                        .currency("EUR")
                        .language("en")
                        .travelClass(1)
                        .adults(1)
                        .build())
                .search(
                    FlightSearchConfig.SearchConfig.builder()
                        .routes(List.of())
                        .maxPricePerFlight(1000)
                        .maxStops(2)
                        .build())
                .notification(
                    FlightSearchConfig.NotificationConfig.builder()
                        .recipientEmail("test@test.com")
                        .senderEmail("sender@test.com")
                        .build())
                .build());

    List<PriceRecordEntity> entities =
        handler.parseFlights(
            response, depId + "-" + arrId, depId, arrId, "2025-07-01", "2025-07-15");

    assertTrue(entities.size() >= 1);
    PriceRecordEntity entity = entities.get(0);
    assertNotNull(entity.getRoute());
    assertNotNull(entity.getTimestamp());
    assertNotNull(entity.getPrice());
    assertNotNull(entity.getDepartureAirportId());
    assertNotNull(entity.getArrivalAirportId());
    assertNotNull(entity.getAirline());
    assertNotNull(entity.getDealType());
  }

  @Provide
  Arbitrary<Integer> validPrice() {
    return Arbitraries.integers().between(50, 2000);
  }

  @Provide
  Arbitrary<String> validAirline() {
    return Arbitraries.of(
        "AirFrance", "Delta", "ANA", "Lufthansa", "BA", "JAL", "Qantas", "Emirates");
  }

  @Provide
  Arbitrary<String> validAirportId() {
    return Arbitraries.of("JFK", "CDG", "LAX", "NRT", "LHR", "FRA", "SYD", "DXB");
  }

  private static FlightDeal createFlight(int price, String airline, String depId, String arrId) {
    return FlightDeal.builder()
        .flights(
            List.of(
                FlightSegment.builder()
                    .departureAirport(
                        Airport.builder()
                            .id(depId)
                            .name(depId + " Airport")
                            .time("2025-07-01 10:00")
                            .build())
                    .arrivalAirport(
                        Airport.builder()
                            .id(arrId)
                            .name(arrId + " Airport")
                            .time("2025-07-01 18:00")
                            .build())
                    .duration(480)
                    .airline(airline)
                    .flightNumber("XX100")
                    .build()))
        .totalDuration(480)
        .price(price)
        .carbonEmissions(CarbonEmissions.builder().thisFlight(150000).build())
        .build();
  }
}
