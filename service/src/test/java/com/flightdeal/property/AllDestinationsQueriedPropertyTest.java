// Feature: flight-deal-notifier, Property 1: All configured routes are queried
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightSearchResponse;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.*;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * Property 1: For any set of configured routes, when the Flight Search Lambda is invoked, the
 * number of flightApiClient.searchFlights() calls equals the number of routes.
 */
class AllDestinationsQueriedPropertyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Property(tries = 100)
  void allConfiguredRoutesAreQueried(@ForAll("routes") List<String> routes) throws Exception {
    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    JsonNode sampleFlight = createSampleFlight();
    when(flightApiClient.searchFlights(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new FlightSearchResponse(List.of(sampleFlight), List.of(), "{}"));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    FlightSearchHandler handler =
        new FlightSearchHandler(
            flightApiClient,
            priceRecordDao,
            snsClient,
            metricsEmitter,
            "arn:aws:sns:us-east-1:123456789:TestTopic",
            routes,
            "2025-07-01",
            "2025-07-15");

    handler.handleRequest(new Object(), null);

    assertEquals(
        routes.size(),
        Mockito.mockingDetails(flightApiClient).getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("searchFlights"))
            .count());
  }

  @Provide
  Arbitrary<List<String>> routes() {
    return Arbitraries.integers()
        .between(1, 10)
        .map(
            count ->
                IntStream.rangeClosed(1, count)
                    .mapToObj(i -> "DEP" + i + "-ARR" + i)
                    .collect(Collectors.toList()));
  }

  private static JsonNode createSampleFlight() {
    ObjectNode flight = MAPPER.createObjectNode();
    flight.put("price", 200);
    flight.put("total_duration", 480);
    ArrayNode flights = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();
    ObjectNode dep = MAPPER.createObjectNode();
    dep.put("id", "DEP");
    dep.put("name", "Departure");
    dep.put("time", "2025-07-01 10:00");
    segment.set("departure_airport", dep);
    ObjectNode arr = MAPPER.createObjectNode();
    arr.put("id", "ARR");
    arr.put("name", "Arrival");
    arr.put("time", "2025-07-01 18:00");
    segment.set("arrival_airport", arr);
    segment.put("airline", "TestAir");
    segment.put("flight_number", "TA100");
    flights.add(segment);
    flight.set("flights", flights);
    return flight;
  }
}
