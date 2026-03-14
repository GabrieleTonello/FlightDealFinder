// Feature: flight-deal-notifier, Property 1: All configured routes are queried
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
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

  @Property(tries = 100)
  void allConfiguredRoutesAreQueried(@ForAll("routes") List<String> routes) throws Exception {
    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    FlightDeal sampleFlight = createSampleFlight();
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
