// Feature: flight-deal-notifier, Property 5: Retry with exponential backoff
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
import software.amazon.awssdk.services.sns.model.SnsException;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 5: For any operation that fails transiently, retry delays are monotonically
 * increasing. Test the SNS publish retry by mocking SnsClient to fail N times then succeed,
 * and verify the number of calls.
 *
 * Validates: Requirements 3.3, 4.3
 */
class RetryBackoffPropertyTest {

    @Property(tries = 100)
    void retryDelaysAreMonotonicallyIncreasing(
            @ForAll("failuresBeforeSuccess") int failuresBeforeSuccess) throws Exception {

        FlightApiClient flightApiClient = mock(FlightApiClient.class);
        PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
        SnsClient snsClient = mock(SnsClient.class);
        MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

        when(flightApiClient.searchDeals("TestDest")).thenReturn(List.of(
                FlightDeal.builder()
                        .destination("TestDest")
                        .price(new BigDecimal("300.00"))
                        .departureDate("2025-06-01")
                        .returnDate("2025-06-08")
                        .airline("TestAir")
                        .build()
        ));

        // Configure SNS to fail N times then succeed
        AtomicInteger callCount = new AtomicInteger(0);
        when(snsClient.publish(any(PublishRequest.class))).thenAnswer(invocation -> {
            int current = callCount.incrementAndGet();
            if (current <= failuresBeforeSuccess) {
                throw SnsException.builder().message("Transient failure").build();
            }
            return PublishResponse.builder().build();
        });

        FlightSearchHandler handler = new FlightSearchHandler(
                flightApiClient, priceRecordDao, snsClient,
                metricsEmitter, "arn:aws:sns:us-east-1:123456789:TestTopic", List.of("TestDest"));

        handler.handleRequest(new Object(), null);

        // SNS_MAX_RETRIES is 3 in FlightSearchHandler
        int expectedCalls = Math.min(failuresBeforeSuccess + 1, 3);
        assertEquals(expectedCalls, callCount.get(),
                "Expected " + expectedCalls + " SNS publish calls for " + failuresBeforeSuccess + " failures");
    }

    @Provide
    Arbitrary<Integer> failuresBeforeSuccess() {
        return Arbitraries.integers().between(0, 4);
    }
}
