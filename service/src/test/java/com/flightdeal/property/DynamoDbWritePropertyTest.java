// Feature: flight-deal-notifier, Property 4: DynamoDB write correctness
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightSearchResponse;
import java.util.List;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * Property 4: For any flight JsonNode, the PriceRecordEntity passed to the DAO has correct
 * partition key (route), sort key (timestamp), and all required fields.
 */
class DynamoDbWritePropertyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Property(tries = 100)
  @SuppressWarnings("unchecked")
  void priceStoreWriteContainsCorrectKeysAndFields(
      @ForAll("validPrice") int price,
      @ForAll("validAirline") String airline,
      @ForAll("validRoute") String route)
      throws Exception {

    String[] parts = route.split("-");
    String depId = parts[0];
    String arrId = parts[1];

    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    JsonNode flight = createFlight(price, airline, depId, arrId);
    when(flightApiClient.searchFlights(depId, arrId, "2025-07-01", "2025-07-15"))
        .thenReturn(new FlightSearchResponse(List.of(flight), List.of(), "{}"));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    FlightSearchHandler handler =
        new FlightSearchHandler(
            flightApiClient,
            priceRecordDao,
            snsClient,
            metricsEmitter,
            "arn:aws:sns:us-east-1:123456789:TestTopic",
            List.of(route),
            "2025-07-01",
            "2025-07-15");

    handler.handleRequest(new Object(), null);

    ArgumentCaptor<List<PriceRecordEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(priceRecordDao).saveBatch(captor.capture());

    List<PriceRecordEntity> entities = captor.getValue();
    assertEquals(1, entities.size());

    PriceRecordEntity entity = entities.get(0);
    assertEquals(route, entity.getRoute());
    assertNotNull(entity.getTimestamp());
    assertEquals(price, entity.getPrice());
    assertEquals(depId, entity.getDepartureAirportId());
    assertEquals(arrId, entity.getArrivalAirportId());
    assertEquals(airline, entity.getAirline());
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
  Arbitrary<String> validRoute() {
    return Arbitraries.of("JFK-CDG", "LAX-NRT", "LHR-FRA", "SYD-DXB", "ORD-FCO");
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
