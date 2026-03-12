// Feature: flight-deal-notifier, Property 4: DynamoDB write correctness
package com.flightdeal.property;

import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.handler.FlightSearchHandler;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 4: For any FlightDeal and timestamp, the PriceRecordEntity passed to the DAO
 * has correct partition key (destination), sort key (timestamp), and all required fields.
 *
 * Validates: Requirements 3.1, 3.2
 */
class DynamoDbWritePropertyTest {

    @Property(tries = 100)
    @SuppressWarnings("unchecked")
    void priceStoreWriteContainsCorrectKeysAndFields(
            @ForAll("validDestination") String destination,
            @ForAll("validPrice") BigDecimal price,
            @ForAll("validDepartureDate") String departureDate,
            @ForAll("validReturnDate") String returnDate,
            @ForAll("validAirline") String airline) throws Exception {

        FlightApiClient flightApiClient = mock(FlightApiClient.class);
        PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
        SnsClient snsClient = mock(SnsClient.class);
        MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

        FlightDeal deal = FlightDeal.builder()
                .destination(destination)
                .price(price)
                .departureDate(departureDate)
                .returnDate(returnDate)
                .airline(airline)
                .build();

        when(flightApiClient.searchDeals(destination)).thenReturn(List.of(deal));
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().build());

        FlightSearchHandler handler = new FlightSearchHandler(
                flightApiClient, priceRecordDao, snsClient,
                metricsEmitter, "arn:aws:sns:us-east-1:123456789:TestTopic", List.of(destination));

        handler.handleRequest(new Object(), null);

        ArgumentCaptor<List<PriceRecordEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(priceRecordDao).saveBatch(captor.capture());

        List<PriceRecordEntity> entities = captor.getValue();
        assertEquals(1, entities.size());

        PriceRecordEntity entity = entities.get(0);
        // Partition key is destination
        assertEquals(destination, entity.getDestination());
        // Sort key (timestamp) is non-null
        assertNotNull(entity.getTimestamp());
        // All required fields present
        assertEquals(price, entity.getPrice());
        assertEquals(departureDate, entity.getDepartureDate());
        assertEquals(returnDate, entity.getReturnDate());
        assertEquals(airline, entity.getAirline());
        assertNotNull(entity.getRetrievalTimestamp());
    }

    @Provide
    Arbitrary<String> validDestination() {
        return Arbitraries.of("Paris", "Tokyo", "London", "Berlin", "Sydney", "Rome", "Madrid", "Dubai");
    }

    @Provide
    Arbitrary<BigDecimal> validPrice() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("50.00"), new BigDecimal("2000.00"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<String> validDepartureDate() {
        return Arbitraries.integers().between(1, 180).map(dayOfYear -> {
            java.time.LocalDate date = java.time.LocalDate.of(2025, 1, 1).plusDays(dayOfYear - 1);
            return date.toString();
        });
    }

    @Provide
    Arbitrary<String> validReturnDate() {
        return Arbitraries.integers().between(181, 365).map(dayOfYear -> {
            java.time.LocalDate date = java.time.LocalDate.of(2025, 1, 1).plusDays(dayOfYear - 1);
            return date.toString();
        });
    }

    @Provide
    Arbitrary<String> validAirline() {
        return Arbitraries.of("AirFrance", "Delta", "ANA", "Lufthansa", "BA", "JAL", "Qantas", "Emirates");
    }
}
