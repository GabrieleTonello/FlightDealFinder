package com.flightdeal.proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlightApiClientTest {

  private HttpClient httpClient;
  private FlightApiClient flightApiClient;

  @BeforeEach
  void setUp() {
    httpClient = mock(HttpClient.class);
    flightApiClient = new FlightApiClient(httpClient, "test-key", Duration.ofSeconds(10));
  }

  @SuppressWarnings("unchecked")
  private void mockResponse(int statusCode, String body) throws Exception {
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(statusCode);
    when(mockResponse.body()).thenReturn(body);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);
  }

  @Test
  void searchFlights_returnsFlightsOnSuccess() throws Exception {
    String responseBody =
        """
        {
          "best_flights": [
            {"price": 299, "total_duration": 480, "flights": [{"airline": "AirFrance"}]}
          ],
          "other_flights": [
            {"price": 499, "total_duration": 600, "flights": [{"airline": "Delta"}]}
          ]
        }
        """;
    mockResponse(200, responseBody);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertEquals(1, response.bestFlights().size());
    assertEquals(1, response.otherFlights().size());
    assertEquals(299, response.bestFlights().get(0).path("price").asInt());
    assertEquals(499, response.otherFlights().get(0).path("price").asInt());
    assertTrue(response.hasFlights());
    assertEquals(2, response.totalFlightCount());
  }

  @Test
  void searchFlights_returnsEmptyWhenNoFlights() throws Exception {
    mockResponse(200, "{\"best_flights\": [], \"other_flights\": []}");

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertTrue(response.bestFlights().isEmpty());
    assertTrue(response.otherFlights().isEmpty());
    assertFalse(response.hasFlights());
  }

  @SuppressWarnings("unchecked")
  @Test
  void searchFlights_throwsOnHttpError() throws Exception {
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(500);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);

    FlightApiException ex =
        assertThrows(
            FlightApiException.class,
            () -> flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15"));

    assertEquals("HTTP_ERROR", ex.getErrorType());
    assertTrue(ex.getMessage().contains("500"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void searchFlights_throwsOnTimeout() throws Exception {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new HttpTimeoutException("Connection timed out"));

    FlightApiException ex =
        assertThrows(
            FlightApiException.class,
            () -> flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15"));

    assertEquals("TIMEOUT", ex.getErrorType());
  }

  @SuppressWarnings("unchecked")
  @Test
  void searchFlights_throwsOnIOException() throws Exception {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("Connection refused"));

    FlightApiException ex =
        assertThrows(
            FlightApiException.class,
            () -> flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15"));

    assertEquals("IO_ERROR", ex.getErrorType());
  }

  @Test
  void searchFlights_throwsOnMalformedJson() throws Exception {
    mockResponse(200, "not valid json");

    FlightApiException ex =
        assertThrows(
            FlightApiException.class,
            () -> flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15"));

    assertEquals("PARSE_ERROR", ex.getErrorType());
  }

  @Test
  void searchFlights_handlesResponseWithOnlyBestFlights() throws Exception {
    mockResponse(200, "{\"best_flights\": [{\"price\": 199}]}");

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertEquals(1, response.bestFlights().size());
    assertTrue(response.otherFlights().isEmpty());
  }

  @Test
  void searchFlights_rawResponsePreserved() throws Exception {
    String rawBody = "{\"best_flights\": [], \"other_flights\": []}";
    mockResponse(200, rawBody);

    FlightSearchResponse response =
        flightApiClient.searchFlights("JFK", "CDG", "2025-07-01", "2025-07-15");

    assertEquals(rawBody, response.rawResponse());
  }
}
