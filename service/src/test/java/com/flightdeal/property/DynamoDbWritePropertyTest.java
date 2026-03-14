// Feature: flight-deal-notifier, Property 4: DynamoDB write correctness
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.flightdeal.config.FlightSearchConfig;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.CarbonEmissions;
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

/**
 * Property 4: For any FlightDeal, the PriceRecordEntity passed to the DAO has correct partition key
 * (route), sort key (timestamp), and all required fields.
 */
class DynamoDbWritePropertyTest {

  @Property(tries = 100)
  @SuppressWarnings("unchecked")
  void priceStoreWriteContainsCorrectKeysAndFields(
      @ForAll("validPrice") int price,
      @ForAll("validAirline") String airline,
      @ForAll("validRoute") String route)
      throws Exception {

    String[] parts = route.split("-");
    String depId = parts[0];
    String arrId = parts[1];

    FlightApiClient flightApiClient = mock(FlightApiClient.class);
    PriceRecordDao priceRecordDao = mock(PriceRecordDao.class);
    SnsClient snsClient = mock(SnsClient.class);
    MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

    FlightDeal deal = createFlight(price, airline, depId, arrId);
    when(flightApiClient.searchFlights(eq(depId), eq(arrId), anyString(), anyString()))
        .thenReturn(new FlightSearchResponse(List.of(deal), List.of(), "{}"));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

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
                        .routes(List.of(route))
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

    ArgumentCaptor<List<PriceRecordEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(priceRecordDao).saveBatch(captor.capture());

    List<PriceRecordEntity> entities = captor.getValue();
    assertEquals(1, entities.size());

    PriceRecordEntity entity = entities.get(0);
    assertEquals(route, entity.getRoute());
    assertNotNull(entity.getTimestamp());
    assertEquals(price, entity.getPrice());
    assertEquals(depId, entity.getDepartureAirportId());
    assertEquals(arrId, entity.getArrivalAirportId());
    assertEquals(airline, entity.getAirline());
  }

  @Provide
  Arbitrary<Integer> validPrice() {
    return Arbitraries.integers().between(50, 2000);
  }

  @Provide
  Arbitrary<String> validAirline() {
    return Arbitraries.of(
        "AirFrance", "Delta", "ANA", "Lufthansa", "BA", "JAL", "Qantas", "Emirates");
  }

  @Provide
  Arbitrary<String> validRoute() {
    return Arbitraries.of("JFK-CDG", "LAX-NRT", "LHR-FRA", "SYD-DXB", "ORD-FCO");
  }

  private static FlightDeal createFlight(int price, String airline, String depId, String arrId) {
    return FlightDeal.builder()
        .flights(
            List.of(
                FlightSegment.builder()
                    .departureAirport(
                        Airport.builder()
                            .id(depId)
                            .name(depId + " Airport")
                            .time("2025-07-01 10:00")
                            .build())
                    .arrivalAirport(
                        Airport.builder()
                            .id(arrId)
                            .name(arrId + " Airport")
                            .time("2025-07-01 18:00")
                            .build())
                    .duration(480)
                    .airline(airline)
                    .flightNumber("XX100")
                    .build()))
        .totalDuration(480)
        .price(price)
        .carbonEmissions(CarbonEmissions.builder().thisFlight(150000).build())
        .build();
  }
}
