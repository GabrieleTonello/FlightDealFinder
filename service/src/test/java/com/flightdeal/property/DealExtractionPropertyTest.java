// Feature: flight-deal-notifier, Property 2: Deal extraction preserves all required fields
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.proxy.FlightSearchResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import net.jqwik.api.*;

/**
 * Property 2: For any valid flight JsonObject, the parsed PriceRecordEntity has all required fields
 * non-null.
 */
class DealExtractionPropertyTest {

  @Property(tries = 100)
  void dealExtractionPreservesAllFields(
      @ForAll("validPrice") int price,
      @ForAll("validAirline") String airline,
      @ForAll("validAirportId") String depId,
      @ForAll("validAirportId") String arrId) {

    JsonObject flight = createFlight(price, airline, depId, arrId);
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

  private static JsonObject createFlight(int price, String airline, String depId, String arrId) {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", price);
    flight.addProperty("total_duration", 480);

    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();

    JsonObject dep = new JsonObject();
    dep.addProperty("id", depId);
    dep.addProperty("name", depId + " Airport");
    dep.addProperty("time", "2025-07-01 10:00");
    segment.add("departure_airport", dep);

    JsonObject arr = new JsonObject();
    arr.addProperty("id", arrId);
    arr.addProperty("name", arrId + " Airport");
    arr.addProperty("time", "2025-07-01 18:00");
    segment.add("arrival_airport", arr);

    segment.addProperty("airline", airline);
    segment.addProperty("flight_number", "XX100");
    flights.add(segment);
    flight.add("flights", flights);

    JsonObject carbon = new JsonObject();
    carbon.addProperty("this_flight", 150000);
    flight.add("carbon_emissions", carbon);

    return flight;
  }
}
