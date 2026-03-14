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
    JsonObject bestDep = new JsonObject();
    bestDep.addProperty("name", "JFK");
    bestDep.addProperty("id", "JFK");
    bestSeg.add("departure_airport", bestDep);
    JsonObject bestArr = new JsonObject();
    bestArr.addProperty("name", "CDG");
    bestArr.addProperty("id", "CDG");
    bestSeg.add("arrival_airport", bestArr);
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
    JsonObject otherDep = new JsonObject();
    otherDep.addProperty("name", "JFK");
    otherDep.addProperty("id", "JFK");
    otherSeg.add("departure_airport", otherDep);
    JsonObject otherArr = new JsonObject();
    otherArr.addProperty("name", "CDG");
    otherArr.addProperty("id", "CDG");
    otherSeg.add("arrival_airport", otherArr);
    otherSegments.add(otherSeg);
    otherFlight.add("flights", otherSegments);
    otherFlights.add(otherFlight);
    result.add("other_flights", otherFlights);

    when(serpApi.search(any(Map.class))).thenReturn(result);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertEquals(1, response.bestFlights().size());
    assertEquals(1, response.otherFlights().size());
    assertEquals(299, response.bestFlights().get(0).getPrice());
    assertEquals(499, response.otherFlights().get(0).getPrice());
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
    flight.addProperty("total_duration", 480);
    JsonArray segs = new JsonArray();
    JsonObject seg = new JsonObject();
    seg.addProperty("airline", "TestAir");
    JsonObject dep = new JsonObject();
    dep.addProperty("name", "JFK");
    dep.addProperty("id", "JFK");
    seg.add("departure_airport", dep);
    JsonObject arr = new JsonObject();
    arr.addProperty("name", "CDG");
    arr.addProperty("id", "CDG");
    seg.add("arrival_airport", arr);
    segs.add(seg);
    flight.add("flights", segs);
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

    when(serpApi.search(any(Map.class))).thenReturn(result);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertTrue(response.bestFlights().isEmpty());
    assertTrue(response.otherFlights().isEmpty());
    assertFalse(response.hasFlights());
  }
}
