// Feature: flight-deal-notifier, Property 3: Per-destination error isolation
package com.flightdeal.property;

import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import net.jqwik.api.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 3: For any set of destinations where a subset fail, the handler still
 * returns deals for successful destinations.
 *
 * Validates: Requirements 2.3
 */
class ErrorIsolationPropertyTest {

    @Property(tries = 100)
    void failedDestinationsDoNotBlockSuccessful(
            @ForAll("destinationSets") DestinationSets sets) throws Exception {

        FlightApiClient flightApiClient = mock(FlightApiClient.class);
        PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
        SnsClient snsClient = mock(SnsClient.class);
        MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().build());

        // Configure successful destinations to return deals
        for (String dest : sets.successful) {
            when(flightApiClient.searchDeals(dest)).thenReturn(List.of(
                    FlightDeal.builder()
                            .destination(dest)
                            .price(new BigDecimal("200.00"))
                            .departureDate("2025-06-01")
                            .returnDate("2025-06-08")
                            .airline("TestAir")
                            .build()
            ));
        }

        // Configure failing destinations to throw
        for (String dest : sets.failing) {
            when(flightApiClient.searchDeals(dest))
                    .thenThrow(new FlightApiException(dest, "API error", "HTTP_ERROR"));
        }

        List<String> allDestinations = new ArrayList<>();
        allDestinations.addAll(sets.successful);
        allDestinations.addAll(sets.failing);

        FlightSearchHandler handler = new FlightSearchHandler(
                flightApiClient, priceRecordDao, snsClient,
                metricsEmitter, "arn:aws:sns:us-east-1:123456789:TestTopic", allDestinations);

        Map<String, Object> result = handler.handleRequest(new Object(), null);

        int dealsFound = (int) result.get("dealsFound");
        // Each successful destination returns exactly 1 deal
        assertTrue(dealsFound >= sets.successful.size(),
                "Should have at least " + sets.successful.size() + " deals but got " + dealsFound);
    }

    @Provide
    Arbitrary<DestinationSets> destinationSets() {
        return Arbitraries.integers().between(1, 5).flatMap(successCount ->
                Arbitraries.integers().between(1, 5).map(failCount -> {
                    List<String> successful = IntStream.rangeClosed(1, successCount)
                            .mapToObj(i -> "SuccessDest" + i).collect(Collectors.toList());
                    List<String> failing = IntStream.rangeClosed(1, failCount)
                            .mapToObj(i -> "FailDest" + i).collect(Collectors.toList());
                    return new DestinationSets(successful, failing);
                }));
    }

    record DestinationSets(List<String> successful, List<String> failing) {}
}
