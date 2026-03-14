package com.flightdeal.proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import serpapi.SerpApi;
import serpapi.SerpApiException;

class FlightApiClientTest {

  private SerpApi serpApi;
  private FlightApiClient flightApiClient;

  @BeforeEach
  void setUp() {
    serpApi = mock(SerpApi.class);
    flightApiClient = new FlightApiClient(serpApi);
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchFlights_returnsFlightsOnSuccess() throws Exception {
    JsonObject result = new JsonObject();

    JsonArray bestFlights = new JsonArray();
    JsonObject bestFlight = new JsonObject();
    bestFlight.addProperty("price", 299);
    bestFlight.addProperty("total_duration", 480);
    JsonArray bestSegments = new JsonArray();
    JsonObject bestSeg = new JsonObject();
    bestSeg.addProperty("airline", "AirFrance");
    bestSegments.add(bestSeg);
    bestFlight.add("flights", bestSegments);
    bestFlights.add(bestFlight);
    result.add("best_flights", bestFlights);

    JsonArray otherFlights = new JsonArray();
    JsonObject otherFlight = new JsonObject();
    otherFlight.addProperty("price", 499);
    otherFlight.addProperty("total_duration", 600);
    JsonArray otherSegments = new JsonArray();
    JsonObject otherSeg = new JsonObject();
    otherSeg.addProperty("airline", "Delta");
    otherSegments.add(otherSeg);
    otherFlight.add("flights", otherSegments);
    otherFlights.add(otherFlight);
    result.add("other_flights", otherFlights);

    when(serpApi.search(any(Map.class))).thenReturn(result);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertEquals(1, response.bestFlights().size());
    assertEquals(1, response.otherFlights().size());
    assertEquals(299, response.bestFlights().get(0).get("price").getAsInt());
    assertEquals(499, response.otherFlights().get(0).get("price").getAsInt());
    assertTrue(response.hasFlights());
    assertEquals(2, response.totalFlightCount());
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchFlights_returnsEmptyWhenNoFlights() throws Exception {
    JsonObject result = new JsonObject();
    result.add("best_flights", new JsonArray());
    result.add("other_flights", new JsonArray());

    when(serpApi.search(any(Map.class))).thenReturn(result);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertTrue(response.bestFlights().isEmpty());
    assertTrue(response.otherFlights().isEmpty());
    assertFalse(response.hasFlights());
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchFlights_throwsOnSerpApiError() throws Exception {
    when(serpApi.search(any(Map.class))).thenThrow(new SerpApiException("Access denied"));

    FlightApiException ex =
        assertThrows(
            FlightApiException.class,
            () -> flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15"));

    assertEquals("API_ERROR", ex.getErrorType());
    assertTrue(ex.getMessage().contains("SerpApi error"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchFlights_handlesResponseWithOnlyBestFlights() throws Exception {
    JsonObject result = new JsonObject();
    JsonArray bestFlights = new JsonArray();
    JsonObject flight = new JsonObject();
    flight.addProperty("price", 199);
    bestFlights.add(flight);
    result.add("best_flights", bestFlights);

    when(serpApi.search(any(Map.class))).thenReturn(result);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertEquals(1, response.bestFlights().size());
    assertTrue(response.otherFlights().isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchFlights_rawResponsePreserved() throws Exception {
    JsonObject result = new JsonObject();
    result.add("best_flights", new JsonArray());
    result.add("other_flights", new JsonArray());

    when(serpApi.search(any(Map.class))).thenReturn(result);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertNotNull(response.rawResponse());
    assertFalse(response.rawResponse().isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchFlights_handlesMissingFlightArrays() throws Exception {
    JsonObject result = new JsonObject();
    // No best_flights or other_flights keys

    when(serpApi.search(any(Map.class))).thenReturn(result);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertTrue(response.bestFlights().isEmpty());
    assertTrue(response.otherFlights().isEmpty());
    assertFalse(response.hasFlights());
  }
}
