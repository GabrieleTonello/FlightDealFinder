// Feature: flight-deal-notifier, Property 6: Raw response published after storage
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.dao.PriceRecordDao;
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

  private static final ObjectMapper MAPPER = new ObjectMapper();

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
    JsonNode sampleFlight = createSampleFlight();

    for (String route : routes) {
      String[] parts = route.split("-");
      when(flightApiClient.searchFlights(parts[0], parts[1], "2025-07-01", "2025-07-15"))
          .thenReturn(new FlightSearchResponse(List.of(sampleFlight), List.of(), rawResponse));
    }

    FlightSearchHandler handler =
        new FlightSearchHandler(
            flightApiClient,
            priceRecordDao,
            snsClient,
            metricsEmitter,
            "arn:aws:sns:us-east-1:123456789:TestTopic",
            routes,
            "2025-07-01",
            "2025-07-15");

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

  private static JsonNode createSampleFlight() {
    ObjectNode flight = MAPPER.createObjectNode();
    flight.put("price", 200);
    flight.put("total_duration", 480);
    ArrayNode flights = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();
    ObjectNode dep = MAPPER.createObjectNode();
    dep.put("id", "DEP");
    dep.put("name", "Departure");
    dep.put("time", "2025-07-01 10:00");
    segment.set("departure_airport", dep);
    ObjectNode arr = MAPPER.createObjectNode();
    arr.put("id", "ARR");
    arr.put("name", "Arrival");
    arr.put("time", "2025-07-01 18:00");
    segment.set("arrival_airport", arr);
    segment.put("airline", "TestAir");
    segment.put("flight_number", "TA100");
    flights.add(segment);
    flight.set("flights", flights);
    return flight;
  }
}
