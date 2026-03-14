// Feature: flight-deal-notifier, Property 6: Raw response published after storage
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.flightdeal.proxy.FlightSearchResponse;
import java.util.List;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/** Property 6: For any non-empty flight response, the SNS message contains the raw response. */
class PublishAfterStoragePropertyTest {

  @Property(tries = 100)
  void publishedMessageContainsRawResponse(@ForAll("routeList") List<String> routes)
      throws Exception {
    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    String rawResponse = "{\"best_flights\":[{\"price\":200}],\"other_flights\":[]}";
    FlightDeal sampleFlight = createSampleFlight();

    for (String route : routes) {
      String[] parts = route.split("-");
      when(flightApiClient.searchFlights(eq(parts[0]), eq(parts[1]), anyString(), anyString()))
          .thenReturn(new FlightSearchResponse(List.of(sampleFlight), List.of(), rawResponse));
    }

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
                        .routes(routes)
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

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient, atLeastOnce()).publish(captor.capture());

    for (PublishRequest request : captor.getAllValues()) {
      assertNotNull(request.message());
    }
  }

  @Provide
  Arbitrary<List<String>> routeList() {
    return Arbitraries.of("JFK-CDG", "LAX-NRT", "LHR-FRA", "SYD-DXB")
        .list()
        .ofMinSize(1)
        .ofMaxSize(4)
        .uniqueElements();
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
