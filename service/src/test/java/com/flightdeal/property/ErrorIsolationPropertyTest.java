// Feature: flight-deal-notifier, Property 3: Per-route error isolation
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import com.flightdeal.proxy.FlightSearchResponse;
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

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Property(tries = 100)
  void failedRoutesDoNotBlockSuccessful(@ForAll("routeSets") RouteSets sets) throws Exception {
    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    JsonNode sampleFlight = createSampleFlight();

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
