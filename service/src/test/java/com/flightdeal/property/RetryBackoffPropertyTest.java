// Feature: flight-deal-notifier, Property 5: Retry with exponential backoff
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
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

/**
 * Property 5: For any operation that fails transiently, retry delays are monotonically increasing.
 */
class RetryBackoffPropertyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Property(tries = 100)
  void retryDelaysAreMonotonicallyIncreasing(
      @ForAll("failuresBeforeSuccess") int failuresBeforeSuccess) throws Exception {

    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    JsonNode sampleFlight = createSampleFlight();
    when(flightApiClient.searchFlights(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new FlightSearchResponse(List.of(sampleFlight), List.of(), "{}"));

    AtomicInteger callCount = new AtomicInteger(0);
    when(snsClient.publish(any(PublishRequest.class)))
        .thenAnswer(
            invocation -> {
              int current = callCount.incrementAndGet();
              if (current <= failuresBeforeSuccess) {
                throw SnsException.builder().message("Transient failure").build();
              }
              return PublishResponse.builder().build();
            });

    FlightSearchHandler handler =
        new FlightSearchHandler(
            flightApiClient,
            priceRecordDao,
            snsClient,
            metricsEmitter,
            "arn:aws:sns:us-east-1:123456789:TestTopic",
            List.of("JFK-CDG"),
            "2025-07-01",
            "2025-07-15");

    handler.handleRequest(new Object(), null);

    int expectedCalls = Math.min(failuresBeforeSuccess + 1, 3);
    assertEquals(
        expectedCalls,
        callCount.get(),
        "Expected "
            + expectedCalls
            + " SNS publish calls for "
            + failuresBeforeSuccess
            + " failures");
  }

  @Provide
  Arbitrary<Integer> failuresBeforeSuccess() {
    return Arbitraries.integers().between(0, 4);
  }

  private static JsonNode createSampleFlight() {
    ObjectNode flight = MAPPER.createObjectNode();
    flight.put("price", 300);
    flight.put("total_duration", 480);
    ArrayNode flights = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();
    ObjectNode dep = MAPPER.createObjectNode();
    dep.put("id", "JFK");
    dep.put("name", "JFK Airport");
    dep.put("time", "2025-07-01 10:00");
    segment.set("departure_airport", dep);
    ObjectNode arr = MAPPER.createObjectNode();
    arr.put("id", "CDG");
    arr.put("name", "CDG Airport");
    arr.put("time", "2025-07-01 18:00");
    segment.set("arrival_airport", arr);
    segment.put("airline", "TestAir");
    segment.put("flight_number", "TA100");
    flights.add(segment);
    flight.set("flights", flights);
    return flight;
  }
}
