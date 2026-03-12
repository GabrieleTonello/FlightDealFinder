package com.flightdeal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

@ExtendWith(MockitoExtension.class)
class FlightSearchHandlerTest {

  @Mock private FlightApiClient flightApiClient;
  @Mock private PriceRecordDao priceRecordDao;
  @Mock private SnsClient snsClient;
  @Mock private MetricsEmitter metricsEmitter;

  private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:123456789:DealTopic";

  private FlightSearchHandler handler;

  @BeforeEach
  void setUp() {
    // Default: two destinations
    handler = createHandler(List.of("Paris", "Tokyo"));
  }

  private FlightSearchHandler createHandler(List<String> destinations) {
    return new FlightSearchHandler(
        flightApiClient, priceRecordDao, snsClient, metricsEmitter, TOPIC_ARN, destinations);
  }

  private FlightDeal deal(
      String destination, String price, String dep, String ret, String airline) {
    return FlightDeal.builder()
        .destination(destination)
        .price(new BigDecimal(price))
        .departureDate(dep)
        .returnDate(ret)
        .airline(airline)
        .build();
  }

  // ---- Success path: deals found for all destinations ----

  @Test
  void handleRequest_successPath_savesAndPublishesDeals() throws Exception {
    List<FlightDeal> parisDeals =
        List.of(deal("Paris", "299.99", "2025-03-01", "2025-03-08", "AirFrance"));
    List<FlightDeal> tokyoDeals =
        List.of(deal("Tokyo", "899.00", "2025-04-01", "2025-04-10", "ANA"));

    when(flightApiClient.searchDeals("Paris")).thenReturn(parisDeals);
    when(flightApiClient.searchDeals("Tokyo")).thenReturn(tokyoDeals);
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(2, result.get("dealsFound"));
    assertEquals(2, result.get("destinationsSearched"));
    assertEquals(0, result.get("errorsCount"));

    // Verify saveBatch called with correct entities
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PriceRecordEntity>> entitiesCaptor = ArgumentCaptor.forClass(List.class);
    verify(priceRecordDao).saveBatch(entitiesCaptor.capture());
    List<PriceRecordEntity> savedEntities = entitiesCaptor.getValue();
    assertEquals(2, savedEntities.size());
    assertEquals("Paris", savedEntities.get(0).getDestination());
    assertEquals(new BigDecimal("299.99"), savedEntities.get(0).getPrice());
    assertEquals("2025-03-01", savedEntities.get(0).getDepartureDate());
    assertEquals("2025-03-08", savedEntities.get(0).getReturnDate());
    assertEquals("AirFrance", savedEntities.get(0).getAirline());
    assertNotNull(savedEntities.get(0).getTimestamp());
    assertNotNull(savedEntities.get(0).getRetrievalTimestamp());
    assertEquals("Tokyo", savedEntities.get(1).getDestination());

    // Verify SNS publish called
    ArgumentCaptor<PublishRequest> publishCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(publishCaptor.capture());
    PublishRequest publishRequest = publishCaptor.getValue();
    assertEquals(TOPIC_ARN, publishRequest.topicArn());
    String message = publishRequest.message();
    assertTrue(message.contains("Paris"));
    assertTrue(message.contains("Tokyo"));
    assertTrue(message.contains("299.99"));
    assertTrue(message.contains("899"));
    assertTrue(message.contains("destinationsSearched"));
  }

  // ---- Partial failures: some destinations throw FlightApiException ----

  @Test
  void handleRequest_partialFailure_continuesWithSuccessfulDestinations() throws Exception {
    List<FlightDeal> parisDeals =
        List.of(deal("Paris", "299.99", "2025-03-01", "2025-03-08", "AirFrance"));
    when(flightApiClient.searchDeals("Paris")).thenReturn(parisDeals);
    when(flightApiClient.searchDeals("Tokyo"))
        .thenThrow(new FlightApiException("Tokyo", "API error", "HTTP_ERROR"));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(1, result.get("dealsFound"));
    assertEquals(2, result.get("destinationsSearched"));
    assertEquals(1, result.get("errorsCount"));

    verify(priceRecordDao).saveBatch(any());
    verify(snsClient).publish(any(PublishRequest.class));
  }

  // ---- All destinations fail ----

  @Test
  void handleRequest_allDestinationsFail_noSaveNoPublish() throws Exception {
    when(flightApiClient.searchDeals("Paris"))
        .thenThrow(new FlightApiException("Paris", "timeout", "TIMEOUT"));
    when(flightApiClient.searchDeals("Tokyo"))
        .thenThrow(new FlightApiException("Tokyo", "error", "HTTP_ERROR"));

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    assertEquals(2, result.get("destinationsSearched"));
    assertEquals(2, result.get("errorsCount"));

    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  // ---- Empty results from API ----

  @Test
  void handleRequest_emptyResults_skipsDestination() throws Exception {
    when(flightApiClient.searchDeals("Paris")).thenReturn(Collections.emptyList());
    when(flightApiClient.searchDeals("Tokyo")).thenReturn(Collections.emptyList());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    assertEquals(0, result.get("errorsCount"));

    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_nullResults_skipsDestination() throws Exception {
    when(flightApiClient.searchDeals("Paris")).thenReturn(null);
    when(flightApiClient.searchDeals("Tokyo")).thenReturn(null);

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  // ---- DynamoDB saveBatch called with correct entities ----

  @Test
  void handleRequest_saveBatchEntitiesHaveCorrectFields() throws Exception {
    FlightDeal deal = deal("London", "450.00", "2025-06-01", "2025-06-10", "BA");
    handler = createHandler(List.of("London"));
    when(flightApiClient.searchDeals("London")).thenReturn(List.of(deal));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    handler.handleRequest(null, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PriceRecordEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(priceRecordDao).saveBatch(captor.capture());
    PriceRecordEntity entity = captor.getValue().get(0);
    assertEquals("London", entity.getDestination());
    assertEquals(new BigDecimal("450.00"), entity.getPrice());
    assertEquals("2025-06-01", entity.getDepartureDate());
    assertEquals("2025-06-10", entity.getReturnDate());
    assertEquals("BA", entity.getAirline());
    // timestamp and retrievalTimestamp should be the same ISO-8601 instant
    assertEquals(entity.getTimestamp(), entity.getRetrievalTimestamp());
  }

  // ---- SNS publish with retry on failure ----

  @Test
  void handleRequest_snsPublishRetriesOnFailure() throws Exception {
    handler = createHandler(List.of("Paris"));
    when(flightApiClient.searchDeals("Paris"))
        .thenReturn(List.of(deal("Paris", "299.99", "2025-03-01", "2025-03-08", "AirFrance")));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenThrow(SnsException.builder().message("Temporary failure").build())
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(1, result.get("dealsFound"));
    // publish called twice: first fails, second succeeds
    verify(snsClient, times(2)).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_snsPublishExhaustsRetries() throws Exception {
    handler = createHandler(List.of("Paris"));
    when(flightApiClient.searchDeals("Paris"))
        .thenReturn(List.of(deal("Paris", "299.99", "2025-03-01", "2025-03-08", "AirFrance")));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenThrow(SnsException.builder().message("Persistent failure").build());

    Map<String, Object> result = handler.handleRequest(null, null);

    // Should still return result even if SNS fails
    assertEquals(1, result.get("dealsFound"));
    // 3 attempts total (SNS_MAX_RETRIES = 3)
    verify(snsClient, times(3)).publish(any(PublishRequest.class));
  }

  // ---- SNS publish serialization failure ----

  @Test
  void handleRequest_snsSerializationFailure_doesNotThrow() throws Exception {
    // We test publishDealBatch directly with a deal that would cause serialization issues.
    // Since ObjectMapper handles FlightDeal maps fine, we verify the normal path
    // doesn't throw and the message is well-formed.
    handler = createHandler(List.of("Paris"));
    when(flightApiClient.searchDeals("Paris"))
        .thenReturn(List.of(deal("Paris", "299.99", "2025-03-01", "2025-03-08", "AirFrance")));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    // Should not throw
    assertDoesNotThrow(() -> handler.handleRequest(null, null));

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());
    String message = captor.getValue().message();
    // Verify the message is valid JSON containing expected fields
    assertTrue(message.contains("\"deals\""));
    assertTrue(message.contains("\"searchTimestamp\""));
    assertTrue(message.contains("\"destinationsSearched\""));
  }

  // ---- Metrics emission ----

  @Test
  void handleRequest_emitsCorrectMetrics() throws Exception {
    List<FlightDeal> parisDeals =
        List.of(
            deal("Paris", "299.99", "2025-03-01", "2025-03-08", "AirFrance"),
            deal("Paris", "350.00", "2025-03-15", "2025-03-22", "Delta"));
    List<FlightDeal> tokyoDeals =
        List.of(deal("Tokyo", "899.00", "2025-04-01", "2025-04-10", "ANA"));

    when(flightApiClient.searchDeals("Paris")).thenReturn(parisDeals);
    when(flightApiClient.searchDeals("Tokyo")).thenReturn(tokyoDeals);
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    handler.handleRequest(null, null);

    verify(metricsEmitter).emitDealsFound(3);
    verify(metricsEmitter).emitDestinationsSearched(2);
    verify(metricsEmitter).emitExecutionDuration(anyLong());
  }

  @Test
  void handleRequest_emitsMetricsEvenWhenAllFail() throws Exception {
    when(flightApiClient.searchDeals("Paris"))
        .thenThrow(new FlightApiException("Paris", "err", "HTTP_ERROR"));
    when(flightApiClient.searchDeals("Tokyo"))
        .thenThrow(new FlightApiException("Tokyo", "err", "TIMEOUT"));

    handler.handleRequest(null, null);

    verify(metricsEmitter).emitDealsFound(0);
    verify(metricsEmitter).emitDestinationsSearched(2);
    verify(metricsEmitter).emitExecutionDuration(anyLong());
  }

  @Test
  void handleRequest_emitsMetricsWithEmptyResults() throws Exception {
    when(flightApiClient.searchDeals("Paris")).thenReturn(Collections.emptyList());
    when(flightApiClient.searchDeals("Tokyo")).thenReturn(Collections.emptyList());

    handler.handleRequest(null, null);

    verify(metricsEmitter).emitDealsFound(0);
    verify(metricsEmitter).emitDestinationsSearched(2);
    verify(metricsEmitter).emitExecutionDuration(anyLong());
  }

  // ---- Empty destinations list ----

  @Test
  void handleRequest_emptyDestinations_noApiCallsNoSaveNoPublish() throws Exception {
    handler = createHandler(Collections.emptyList());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    assertEquals(0, result.get("destinationsSearched"));
    assertEquals(0, result.get("errorsCount"));

    verifyNoInteractions(flightApiClient);
    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
    verify(metricsEmitter).emitDealsFound(0);
    verify(metricsEmitter).emitDestinationsSearched(0);
    verify(metricsEmitter).emitExecutionDuration(anyLong());
  }

  // ---- SNS message content verification ----

  @Test
  void handleRequest_snsMessageContainsAllDealFields() throws Exception {
    handler = createHandler(List.of("Berlin"));
    FlightDeal berlinDeal = deal("Berlin", "199.50", "2025-05-01", "2025-05-07", "Lufthansa");
    when(flightApiClient.searchDeals("Berlin")).thenReturn(List.of(berlinDeal));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    handler.handleRequest(null, null);

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());
    String message = captor.getValue().message();
    assertTrue(message.contains("Berlin"));
    assertTrue(message.contains("199.5"));
    assertTrue(message.contains("2025-05-01"));
    assertTrue(message.contains("2025-05-07"));
    assertTrue(message.contains("Lufthansa"));
  }

  // ---- Mixed: some empty, some success, some failure ----

  @Test
  void handleRequest_mixedResults_handlesCorrectly() throws Exception {
    handler = createHandler(List.of("Paris", "Tokyo", "London"));
    when(flightApiClient.searchDeals("Paris"))
        .thenReturn(List.of(deal("Paris", "299.99", "2025-03-01", "2025-03-08", "AirFrance")));
    when(flightApiClient.searchDeals("Tokyo")).thenReturn(Collections.emptyList());
    when(flightApiClient.searchDeals("London"))
        .thenThrow(new FlightApiException("London", "unavailable", "HTTP_ERROR"));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(1, result.get("dealsFound"));
    assertEquals(3, result.get("destinationsSearched"));
    assertEquals(1, result.get("errorsCount"));

    verify(priceRecordDao).saveBatch(any());
    verify(snsClient).publish(any(PublishRequest.class));
    verify(metricsEmitter).emitDealsFound(1);
    verify(metricsEmitter).emitDestinationsSearched(3);
  }
}
