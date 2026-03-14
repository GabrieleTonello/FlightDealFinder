// Feature: flight-deal-notifier, Property 2: Deal extraction preserves all required fields
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.proxy.FlightSearchResponse;
import java.util.List;
import net.jqwik.api.*;

/**
 * Property 2: For any valid flight JsonNode, the parsed PriceRecordEntity has all required fields
 * non-null.
 */
class DealExtractionPropertyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Property(tries = 100)
  void dealExtractionPreservesAllFields(
      @ForAll("validPrice") int price,
      @ForAll("validAirline") String airline,
      @ForAll("validAirportId") String depId,
      @ForAll("validAirportId") String arrId) {

    JsonNode flight = createFlight(price, airline, depId, arrId);
    FlightSearchResponse response = new FlightSearchResponse(List.of(flight), List.of(), "{}");

    FlightSearchHandler handler =
        new FlightSearchHandler(
            null, null, null, null, null, List.of(), "2025-07-01", "2025-07-15");

    List<PriceRecordEntity> entities =
        handler.parseFlights(response, depId + "-" + arrId, depId, arrId);

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

  private static JsonNode createFlight(int price, String airline, String depId, String arrId) {
    ObjectNode flight = MAPPER.createObjectNode();
    flight.put("price", price);
    flight.put("total_duration", 480);

    ArrayNode flights = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();

    ObjectNode dep = MAPPER.createObjectNode();
    dep.put("id", depId);
    dep.put("name", depId + " Airport");
    dep.put("time", "2025-07-01 10:00");
    segment.set("departure_airport", dep);

    ObjectNode arr = MAPPER.createObjectNode();
    arr.put("id", arrId);
    arr.put("name", arrId + " Airport");
    arr.put("time", "2025-07-01 18:00");
    segment.set("arrival_airport", arr);

    segment.put("airline", airline);
    segment.put("flight_number", "XX100");
    flights.add(segment);
    flight.set("flights", flights);

    ObjectNode carbon = MAPPER.createObjectNode();
    carbon.put("this_flight", 150000);
    flight.set("carbon_emissions", carbon);

    return flight;
  }
}
