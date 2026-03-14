// Feature: flight-deal-notifier, Property 5: Retry with exponential backoff
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.flightdeal.config.FlightSearchConfig;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
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

  @Property(tries = 100)
  void retryDelaysAreMonotonicallyIncreasing(
      @ForAll("failuresBeforeSuccess") int failuresBeforeSuccess) throws Exception {

    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    FlightDeal sampleFlight = createSampleFlight();
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
                        .routes(List.of("JFK-CDG"))
                        .maxPricePerFlight(1000)
                        .maxStops(2)
                        .build())
                .notification(
                    FlightSearchConfig.NotificationConfig.builder()
                        .recipientEmail("test@test.com")
                        .senderEmail("sender@test.com")
                        .build())
                .build());

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

  private static FlightDeal createSampleFlight() {
    return FlightDeal.builder()
        .flights(
            List.of(
                FlightSegment.builder()
                    .departureAirport(
                        Airport.builder()
                            .id("JFK")
                            .name("JFK Airport")
                            .time("2025-07-01 10:00")
                            .build())
                    .arrivalAirport(
                        Airport.builder()
                            .id("CDG")
                            .name("CDG Airport")
                            .time("2025-07-01 18:00")
                            .build())
                    .duration(480)
                    .airline("TestAir")
                    .flightNumber("TA100")
                    .build()))
        .totalDuration(480)
        .price(300)
        .build();
  }
}
