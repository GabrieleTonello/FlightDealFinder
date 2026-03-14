package com.flightdeal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import com.flightdeal.proxy.FlightSearchResponse;
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

  private static FlightSearchConfig createConfig(List<String> routes) {
    return FlightSearchConfig.builder()
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
        .build();
  }

  @BeforeEach
  void setUp() {
    handler = createHandler(List.of("JFK-CDG", "LAX-NRT"));
  }

  private FlightSearchHandler createHandler(List<String> routes) {
    return new FlightSearchHandler(
        flightApiClient,
        priceRecordDao,
        snsClient,
        metricsEmitter,
        TOPIC_ARN,
        createConfig(routes));
  }

  static FlightDeal flightNode(
      int price,
      String depId,
      String depName,
      String depTime,
      String arrId,
      String arrName,
      String arrTime,
      String airline,
      int totalDuration,
      String flightNumber) {
    return FlightDeal.builder()
        .flights(
            List.of(
                FlightSegment.builder()
                    .departureAirport(
                        Airport.builder().id(depId).name(depName).time(depTime).build())
                    .arrivalAirport(Airport.builder().id(arrId).name(arrName).time(arrTime).build())
                    .airline(airline)
                    .flightNumber(flightNumber)
                    .duration(totalDuration)
                    .build()))
        .totalDuration(totalDuration)
        .price(price)
        .carbonEmissions(CarbonEmissions.builder().thisFlight(150000).build())
        .build();
  }

  static FlightDeal sampleFlight(String depId, String arrId, int price) {
    return flightNode(
        price,
        depId,
        depId + " Airport",
        "2025-07-01 10:00",
        arrId,
        arrId + " Airport",
        "2025-07-01 18:00",
        "TestAir",
        480,
        "TA100");
  }

  private FlightSearchResponse responseWith(List<FlightDeal> best, List<FlightDeal> other) {
    return new FlightSearchResponse(best, other, "{\"best_flights\":[],\"other_flights\":[]}");
  }

  @Test
  void handleRequest_successPath_savesAndPublishes() throws Exception {
    FlightDeal jfkCdg = sampleFlight("JFK", "CDG", 299);
    FlightDeal laxNrt = sampleFlight("LAX", "NRT", 899);

    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(jfkCdg), List.of()));
    when(flightApiClient.searchFlights(eq("LAX"), eq("NRT"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(laxNrt), List.of()));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(2, result.get("dealsFound"));
    assertEquals(2, result.get("routesSearched"));
    assertEquals(0, result.get("errorsCount"));

    verify(priceRecordDao, times(2)).saveBatch(any());
    verify(snsClient, times(2)).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_partialFailure_continuesWithSuccessfulRoutes() throws Exception {
    FlightDeal jfkCdg = sampleFlight("JFK", "CDG", 299);
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(jfkCdg), List.of()));
    when(flightApiClient.searchFlights(eq("LAX"), eq("NRT"), anyString(), anyString()))
        .thenThrow(new FlightApiException("LAX->NRT", "API error", "HTTP_ERROR"));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(1, result.get("dealsFound"));
    assertEquals(2, result.get("routesSearched"));
    assertEquals(1, result.get("errorsCount"));
    verify(priceRecordDao).saveBatch(any());
  }

  @Test
  void handleRequest_allRoutesFail_noSaveNoPublish() throws Exception {
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenThrow(new FlightApiException("JFK->CDG", "timeout", "TIMEOUT"));
    when(flightApiClient.searchFlights(eq("LAX"), eq("NRT"), anyString(), anyString()))
        .thenThrow(new FlightApiException("LAX->NRT", "error", "HTTP_ERROR"));

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    assertEquals(2, result.get("errorsCount"));
    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_emptyResults_skipsRoute() throws Exception {
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(), List.of()));
    when(flightApiClient.searchFlights(eq("LAX"), eq("NRT"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(), List.of()));

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void handleRequest_saveBatchEntitiesHaveCorrectFields() throws Exception {
    FlightDeal flight =
        flightNode(
            450,
            "JFK",
            "John F. Kennedy",
            "2025-07-01 08:00",
            "CDG",
            "Charles de Gaulle",
            "2025-07-01 20:00",
            "AirFrance",
            720,
            "AF001");
    handler = createHandler(List.of("JFK-CDG"));
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(flight), List.of()));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    handler.handleRequest(null, null);

    ArgumentCaptor<List<PriceRecordEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(priceRecordDao).saveBatch(captor.capture());
    PriceRecordEntity entity = captor.getValue().get(0);
    assertEquals("JFK-CDG", entity.getRoute());
    assertEquals(450, entity.getPrice());
    assertEquals("JFK", entity.getDepartureAirportId());
    assertEquals("John F. Kennedy", entity.getDepartureAirportName());
    assertEquals("2025-07-01 08:00", entity.getDepartureTime());
    assertEquals("CDG", entity.getArrivalAirportId());
    assertEquals("Charles de Gaulle", entity.getArrivalAirportName());
    assertEquals("2025-07-01 20:00", entity.getArrivalTime());
    assertEquals("AirFrance", entity.getAirline());
    assertEquals(720, entity.getTotalDuration());
    assertEquals(1, entity.getSegments());
    assertEquals("AF001", entity.getFlightNumber());
    assertEquals("best", entity.getDealType());
    assertEquals(150000, entity.getCarbonEmissions());
    assertNotNull(entity.getOutboundDate());
    assertNotNull(entity.getReturnDate());
    assertNotNull(entity.getTimestamp());
  }

  @Test
  void handleRequest_snsPublishRetriesOnFailure() throws Exception {
    handler = createHandler(List.of("JFK-CDG"));
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(sampleFlight("JFK", "CDG", 299)), List.of()));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenThrow(SnsException.builder().message("Temporary failure").build())
        .thenReturn(PublishResponse.builder().build());

    handler.handleRequest(null, null);

    verify(snsClient, times(2)).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_snsPublishExhaustsRetries() throws Exception {
    handler = createHandler(List.of("JFK-CDG"));
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(sampleFlight("JFK", "CDG", 299)), List.of()));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenThrow(SnsException.builder().message("Persistent failure").build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(1, result.get("dealsFound"));
    verify(snsClient, times(3)).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_emitsCorrectMetrics() throws Exception {
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(
            responseWith(
                List.of(sampleFlight("JFK", "CDG", 299), sampleFlight("JFK", "CDG", 350)),
                List.of()));
    when(flightApiClient.searchFlights(eq("LAX"), eq("NRT"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(sampleFlight("LAX", "NRT", 899)), List.of()));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    handler.handleRequest(null, null);

    verify(metricsEmitter).emitDealsFound(3);
    verify(metricsEmitter).emitDestinationsSearched(2);
    verify(metricsEmitter).emitExecutionDuration(anyLong());
  }

  @Test
  void handleRequest_emptyRoutes_noApiCallsNoSaveNoPublish() throws Exception {
    handler = createHandler(Collections.emptyList());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    assertEquals(0, result.get("routesSearched"));
    assertEquals(0, result.get("errorsCount"));

    verifyNoInteractions(flightApiClient);
    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_snsPublishesRawResponse() throws Exception {
    String rawJson = "{\"best_flights\":[{\"price\":299}],\"other_flights\":[]}";
    FlightSearchResponse response =
        new FlightSearchResponse(List.of(sampleFlight("JFK", "CDG", 299)), List.of(), rawJson);
    handler = createHandler(List.of("JFK-CDG"));
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(response);
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    handler.handleRequest(null, null);

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());
    assertEquals(rawJson, captor.getValue().message());
    assertEquals(TOPIC_ARN, captor.getValue().topicArn());
  }

  @Test
  void parseFlightNode_missingFields_usesDefaults() throws Exception {
    // Flight with minimal fields - segment with no airports, no airline info, no carbon
    FlightDeal flight =
        FlightDeal.builder()
            .flights(
                List.of(
                    FlightSegment.builder()
                        .departureAirport(Airport.builder().name("").id("").build())
                        .arrivalAirport(Airport.builder().name("").id("").build())
                        .duration(0)
                        .airline("")
                        .build()))
            .totalDuration(0)
            .price(100)
            .build();

    handler = createHandler(List.of("JFK-CDG"));
    FlightSearchResponse response = new FlightSearchResponse(List.of(flight), List.of(), "{}");
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(response);
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);
    assertEquals(1, result.get("dealsFound"));
  }

  @Test
  void parseFlightNode_emptyFlightsList_returnsNull() {
    FlightDeal flight = FlightDeal.builder().flights(List.of()).totalDuration(0).price(100).build();

    PriceRecordEntity entity =
        handler.parseFlightNode(
            flight, "JFK-CDG", "2025-01-01", "best", "2025-07-01", "2025-07-15");
    assertNull(entity);
  }

  @Test
  void parseFlightNode_carbonEmissionsWithoutThisFlight_returnsNullCarbon() {
    FlightDeal flight =
        FlightDeal.builder()
            .flights(
                List.of(
                    FlightSegment.builder()
                        .departureAirport(
                            Airport.builder()
                                .id("JFK")
                                .name("JFK")
                                .time("2025-07-01 10:00")
                                .build())
                        .arrivalAirport(
                            Airport.builder()
                                .id("CDG")
                                .name("CDG")
                                .time("2025-07-01 18:00")
                                .build())
                        .duration(480)
                        .airline("TestAir")
                        .flightNumber("TA100")
                        .build()))
            .totalDuration(300)
            .price(200)
            .carbonEmissions(CarbonEmissions.builder().build())
            .build();

    PriceRecordEntity entity =
        handler.parseFlightNode(
            flight, "JFK-CDG", "2025-01-01", "best", "2025-07-01", "2025-07-15");
    assertNotNull(entity);
    assertNull(entity.getCarbonEmissions());
  }

  @Test
  void handleRequest_bestAndOtherFlightsParsed() throws Exception {
    FlightDeal best = sampleFlight("JFK", "CDG", 299);
    FlightDeal other = sampleFlight("JFK", "CDG", 499);
    handler = createHandler(List.of("JFK-CDG"));
    when(flightApiClient.searchFlights(eq("JFK"), eq("CDG"), anyString(), anyString()))
        .thenReturn(responseWith(List.of(best), List.of(other)));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(2, result.get("dealsFound"));
  }
}
