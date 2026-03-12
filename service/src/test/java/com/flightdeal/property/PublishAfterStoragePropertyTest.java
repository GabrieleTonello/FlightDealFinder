// Feature: flight-deal-notifier, Property 6: Deal batch published after storage
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * Property 6: For any non-empty list of deals stored, the SNS message contains exactly those deals.
 * Capture the SNS PublishRequest and verify the message body contains all deal destinations.
 *
 * <p>Validates: Requirements 4.1
 */
class PublishAfterStoragePropertyTest {

  @Property(tries = 100)
  void publishedDealsMatchStoredDeals(@ForAll("dealList") List<DealInput> dealInputs)
      throws Exception {
    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    // All deals come from a single destination for simplicity
    List<FlightDeal> deals =
        dealInputs.stream()
            .map(
                input ->
                    FlightDeal.builder()
                        .destination(input.destination)
                        .price(input.price)
                        .departureDate("2025-06-01")
                        .returnDate("2025-06-08")
                        .airline(input.airline)
                        .build())
            .collect(Collectors.toList());

    // Group deals by destination and configure mock
    var byDest = deals.stream().collect(Collectors.groupingBy(FlightDeal::getDestination));
    List<String> destinations = byDest.keySet().stream().toList();

    for (var entry : byDest.entrySet()) {
      when(flightApiClient.searchDeals(entry.getKey())).thenReturn(entry.getValue());
    }

    FlightSearchHandler handler =
        new FlightSearchHandler(
            flightApiClient,
            priceRecordDao,
            snsClient,
            metricsEmitter,
            "arn:aws:sns:us-east-1:123456789:TestTopic",
            destinations);

    handler.handleRequest(new Object(), null);

    // Capture the SNS publish request
    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());

    String messageBody = captor.getValue().message();

    // Verify all deal destinations appear in the published message
    for (FlightDeal deal : deals) {
      assertTrue(
          messageBody.contains(deal.getDestination()),
          "Published message should contain destination: " + deal.getDestination());
    }
  }

  @Provide
  Arbitrary<List<DealInput>> dealList() {
    Arbitrary<DealInput> dealArb =
        Arbitraries.of("Paris", "Tokyo", "London", "Berlin")
            .flatMap(
                dest ->
                    Arbitraries.bigDecimals()
                        .between(new BigDecimal("50.00"), new BigDecimal("2000.00"))
                        .ofScale(2)
                        .flatMap(
                            price ->
                                Arbitraries.of("AirFrance", "Delta", "ANA", "Lufthansa")
                                    .map(airline -> new DealInput(dest, price, airline))));
    return dealArb.list().ofMinSize(1).ofMaxSize(5);
  }

  record DealInput(String destination, BigDecimal price, String airline) {}
}
