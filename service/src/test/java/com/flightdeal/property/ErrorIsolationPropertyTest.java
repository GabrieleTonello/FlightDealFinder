// Feature: flight-deal-notifier, Property 3: Per-route error isolation
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.flightdeal.config.FlightSearchConfig;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
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

  @Property(tries = 100)
  void failedRoutesDoNotBlockSuccessful(@ForAll("routeSets") RouteSets sets) throws Exception {
    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    FlightDeal sampleFlight = createSampleFlight();

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
                        .routes(allRoutes)
                        .maxPricePerFlight(1000)
                        .maxStops(2)
                        .build())
                .notification(
                    FlightSearchConfig.NotificationConfig.builder()
                        .recipientEmail("test@test.com")
                        .senderEmail("sender@test.com")
                        .build())
                .build());

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

  private static FlightDeal createSampleFlight() {
    return FlightDeal.builder()
        .flights(
            List.of(
                FlightSegment.builder()
                    .departureAirport(
                        Airport.builder()
                            .id("DEP")
                            .name("Departure")
                            .time("2025-07-01 10:00")
                            .build())
                    .arrivalAirport(
                        Airport.builder()
                            .id("ARR")
                            .name("Arrival")
                            .time("2025-07-01 18:00")
                            .build())
                    .duration(480)
                    .airline("TestAir")
                    .flightNumber("TA100")
                    .build()))
        .totalDuration(480)
        .price(200)
        .build();
  }
}
