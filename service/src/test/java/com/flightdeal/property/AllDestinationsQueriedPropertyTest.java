// Feature: flight-deal-notifier, Property 1: All configured routes are queried
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightSearchResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

  @Property(tries = 100)
  void allConfiguredRoutesAreQueried(@ForAll("routes") List<String> routes) throws Exception {
    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    JsonObject sampleFlight = createSampleFlight();
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

  private static JsonObject createSampleFlight() {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", 200);
    flight.addProperty("total_duration", 480);
    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();
    JsonObject dep = new JsonObject();
    dep.addProperty("id", "DEP");
    dep.addProperty("name", "Departure");
    dep.addProperty("time", "2025-07-01 10:00");
    segment.add("departure_airport", dep);
    JsonObject arr = new JsonObject();
    arr.addProperty("id", "ARR");
    arr.addProperty("name", "Arrival");
    arr.addProperty("time", "2025-07-01 18:00");
    segment.add("arrival_airport", arr);
    segment.addProperty("airline", "TestAir");
    segment.addProperty("flight_number", "TA100");
    flights.add(segment);
    flight.add("flights", flights);
    return flight;
  }
}
