package com.flightdeal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.dao.PriceRecordEntity;
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
  private static final String OUTBOUND_DATE = "2025-07-01";
  private static final String RETURN_DATE = "2025-07-15";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private FlightSearchHandler handler;

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
        routes,
        OUTBOUND_DATE,
        RETURN_DATE);
  }

  static JsonNode flightNode(
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
    ObjectNode flight = MAPPER.createObjectNode();
    flight.put("price", price);
    flight.put("total_duration", totalDuration);

    ArrayNode flightsArray = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();

    ObjectNode depAirport = MAPPER.createObjectNode();
    depAirport.put("id", depId);
    depAirport.put("name", depName);
    depAirport.put("time", depTime);
    segment.set("departure_airport", depAirport);

    ObjectNode arrAirport = MAPPER.createObjectNode();
    arrAirport.put("id", arrId);
    arrAirport.put("name", arrName);
    arrAirport.put("time", arrTime);
    segment.set("arrival_airport", arrAirport);

    segment.put("airline", airline);
    segment.put("flight_number", flightNumber);
    segment.put("duration", totalDuration);

    flightsArray.add(segment);
    flight.set("flights", flightsArray);

    ObjectNode carbonEmissions = MAPPER.createObjectNode();
    carbonEmissions.put("this_flight", 150000);
    flight.set("carbon_emissions", carbonEmissions);

    return flight;
  }

  static JsonNode sampleFlight(String depId, String arrId, int price) {
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

  private FlightSearchResponse responseWith(List<JsonNode> best, List<JsonNode> other) {
    return new FlightSearchResponse(best, other, "{\"best_flights\":[],\"other_flights\":[]}");
  }

  @Test
  void handleRequest_successPath_savesAndPublishes() throws Exception {
    JsonNode jfkCdg = sampleFlight("JFK", "CDG", 299);
    JsonNode laxNrt = sampleFlight("LAX", "NRT", 899);

    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
        .thenReturn(responseWith(List.of(jfkCdg), List.of()));
    when(flightApiClient.searchFlights("LAX", "NRT", OUTBOUND_DATE, RETURN_DATE))
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
    JsonNode jfkCdg = sampleFlight("JFK", "CDG", 299);
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
        .thenReturn(responseWith(List.of(jfkCdg), List.of()));
    when(flightApiClient.searchFlights("LAX", "NRT", OUTBOUND_DATE, RETURN_DATE))
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
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
        .thenThrow(new FlightApiException("JFK->CDG", "timeout", "TIMEOUT"));
    when(flightApiClient.searchFlights("LAX", "NRT", OUTBOUND_DATE, RETURN_DATE))
        .thenThrow(new FlightApiException("LAX->NRT", "error", "HTTP_ERROR"));

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    assertEquals(2, result.get("errorsCount"));
    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_emptyResults_skipsRoute() throws Exception {
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
        .thenReturn(responseWith(List.of(), List.of()));
    when(flightApiClient.searchFlights("LAX", "NRT", OUTBOUND_DATE, RETURN_DATE))
        .thenReturn(responseWith(List.of(), List.of()));

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(0, result.get("dealsFound"));
    verify(priceRecordDao, never()).saveBatch(any());
    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void handleRequest_saveBatchEntitiesHaveCorrectFields() throws Exception {
    JsonNode flight =
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
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
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
    assertEquals(OUTBOUND_DATE, entity.getOutboundDate());
    assertEquals(RETURN_DATE, entity.getReturnDate());
    assertNotNull(entity.getTimestamp());
  }

  @Test
  void handleRequest_snsPublishRetriesOnFailure() throws Exception {
    handler = createHandler(List.of("JFK-CDG"));
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
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
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
        .thenReturn(responseWith(List.of(sampleFlight("JFK", "CDG", 299)), List.of()));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenThrow(SnsException.builder().message("Persistent failure").build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(1, result.get("dealsFound"));
    verify(snsClient, times(3)).publish(any(PublishRequest.class));
  }

  @Test
  void handleRequest_emitsCorrectMetrics() throws Exception {
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
        .thenReturn(
            responseWith(
                List.of(sampleFlight("JFK", "CDG", 299), sampleFlight("JFK", "CDG", 350)),
                List.of()));
    when(flightApiClient.searchFlights("LAX", "NRT", OUTBOUND_DATE, RETURN_DATE))
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
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
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
  void handleRequest_bestAndOtherFlightsParsed() throws Exception {
    JsonNode best = sampleFlight("JFK", "CDG", 299);
    JsonNode other = sampleFlight("JFK", "CDG", 499);
    handler = createHandler(List.of("JFK-CDG"));
    when(flightApiClient.searchFlights("JFK", "CDG", OUTBOUND_DATE, RETURN_DATE))
        .thenReturn(responseWith(List.of(best), List.of(other)));
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    Map<String, Object> result = handler.handleRequest(null, null);

    assertEquals(2, result.get("dealsFound"));
  }
}
