// Feature: flight-deal-notifier, Property 3: Per-route error isolation
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import com.flightdeal.proxy.FlightSearchResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * Property 3: For any set of routes where a subset fail, the handler still returns deals for
 * successful routes.
 */
class ErrorIsolationPropertyTest {

  @Property(tries = 100)
  void failedRoutesDoNotBlockSuccessful(@ForAll("routeSets") RouteSets sets) throws Exception {
    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    JsonObject sampleFlight = createSampleFlight();

    for (String route : sets.successful) {
      String[] parts = route.split("-");
      when(flightApiClient.searchFlights(eq(parts[0]), eq(parts[1]), anyString(), anyString()))
          .thenReturn(new FlightSearchResponse(List.of(sampleFlight), List.of(), "{}"));
    }

    for (String route : sets.failing) {
      String[] parts = route.split("-");
      when(flightApiClient.searchFlights(eq(parts[0]), eq(parts[1]), anyString(), anyString()))
          .thenThrow(new FlightApiException(route, "API error", "HTTP_ERROR"));
    }

    List<String> allRoutes = new ArrayList<>();
    allRoutes.addAll(sets.successful);
    allRoutes.addAll(sets.failing);

    FlightSearchHandler handler =
        new FlightSearchHandler(
            flightApiClient,
            priceRecordDao,
            snsClient,
            metricsEmitter,
            "arn:aws:sns:us-east-1:123456789:TestTopic",
            allRoutes,
            "2025-07-01",
            "2025-07-15");

    Map<String, Object> result = handler.handleRequest(new Object(), null);

    int dealsFound = (int) result.get("dealsFound");
    assertTrue(
        dealsFound >= sets.successful.size(),
        "Should have at least " + sets.successful.size() + " deals but got " + dealsFound);
  }

  @Provide
  Arbitrary<RouteSets> routeSets() {
    return Arbitraries.integers()
        .between(1, 5)
        .flatMap(
            successCount ->
                Arbitraries.integers()
                    .between(1, 5)
                    .map(
                        failCount -> {
                          List<String> successful =
                              IntStream.rangeClosed(1, successCount)
                                  .mapToObj(i -> "SUC" + i + "-ARR" + i)
                                  .collect(Collectors.toList());
                          List<String> failing =
                              IntStream.rangeClosed(1, failCount)
                                  .mapToObj(i -> "FAL" + i + "-ERR" + i)
                                  .collect(Collectors.toList());
                          return new RouteSets(successful, failing);
                        }));
  }

  record RouteSets(List<String> successful, List<String> failing) {}

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
