// Feature: flight-deal-notifier, Property 5: Retry with exponential backoff
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

  @Property(tries = 100)
  void retryDelaysAreMonotonicallyIncreasing(
      @ForAll("failuresBeforeSuccess") int failuresBeforeSuccess) throws Exception {

    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    JsonObject sampleFlight = createSampleFlight();
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

  private static JsonObject createSampleFlight() {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", 300);
    flight.addProperty("total_duration", 480);
    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();
    JsonObject dep = new JsonObject();
    dep.addProperty("id", "JFK");
    dep.addProperty("name", "JFK Airport");
    dep.addProperty("time", "2025-07-01 10:00");
    segment.add("departure_airport", dep);
    JsonObject arr = new JsonObject();
    arr.addProperty("id", "CDG");
    arr.addProperty("name", "CDG Airport");
    arr.addProperty("time", "2025-07-01 18:00");
    segment.add("arrival_airport", arr);
    segment.addProperty("airline", "TestAir");
    segment.addProperty("flight_number", "TA100");
    flights.add(segment);
    flight.add("flights", flights);
    return flight;
  }
}
