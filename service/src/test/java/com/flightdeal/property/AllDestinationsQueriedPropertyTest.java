// Feature: flight-deal-notifier, Property 1: All configured destinations are queried
package com.flightdeal.property;

import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import net.jqwik.api.*;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 1: For any set of configured destinations, when the Flight Search Lambda
 * is invoked, the number of flightApiClient.searchDeals() calls equals the number of destinations.
 *
 * Validates: Requirements 2.1
 */
class AllDestinationsQueriedPropertyTest {

    @Property(tries = 100)
    void allConfiguredDestinationsAreQueried(@ForAll("destinations") List<String> destinations) throws Exception {
        FlightApiClient flightApiClient = mock(FlightApiClient.class);
        PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
        SnsClient snsClient = mock(SnsClient.class);
        MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

        when(flightApiClient.searchDeals(anyString())).thenReturn(List.of(
                FlightDeal.builder()
                        .destination("TestDest")
                        .price(new BigDecimal("100.00"))
                        .departureDate("2025-06-01")
                        .returnDate("2025-06-08")
                        .airline("TestAir")
                        .build()
        ));
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().build());

        FlightSearchHandler handler = new FlightSearchHandler(
                flightApiClient, priceRecordDao, snsClient,
                metricsEmitter, "arn:aws:sns:us-east-1:123456789:TestTopic", destinations);

        handler.handleRequest(new Object(), null);

        assertEquals(destinations.size(), Mockito.mockingDetails(flightApiClient).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("searchDeals"))
                .count());
    }

    @Provide
    Arbitrary<List<String>> destinations() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .list().ofMinSize(1).ofMaxSize(10)
                .filter(list -> list.stream().allMatch(s -> !s.isBlank()));
    }
}
