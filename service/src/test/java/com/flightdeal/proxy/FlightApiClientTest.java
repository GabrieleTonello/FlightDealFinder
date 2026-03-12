package com.flightdeal.proxy;

import com.flightdeal.generated.model.FlightDeal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlightApiClientTest {

    private HttpClient httpClient;
    private FlightApiClient flightApiClient;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        flightApiClient = new FlightApiClient(httpClient, "https://api.example.com", "test-key", Duration.ofSeconds(10));
    }

    @Test
    void searchDeals_returnsDealsOnSuccess() throws Exception {
        String responseBody = """
                [
                    {
                        "destination": "Paris",
                        "price": 299.99,
                        "departureDate": "2025-03-01",
                        "returnDate": "2025-03-08",
                        "airline": "AirFrance"
                    },
                    {
                        "destination": "Paris",
                        "price": 349.50,
                        "departureDate": "2025-03-15",
                        "returnDate": "2025-03-22",
                        "airline": "Delta"
                    }
                ]
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        List<FlightDeal> deals = flightApiClient.searchDeals("Paris");

        assertEquals(2, deals.size());
        assertEquals("Paris", deals.get(0).getDestination());
        assertEquals(new BigDecimal("299.99"), deals.get(0).getPrice());
        assertEquals("2025-03-01", deals.get(0).getDepartureDate());
        assertEquals("2025-03-08", deals.get(0).getReturnDate());
        assertEquals("AirFrance", deals.get(0).getAirline());
    }

    @Test
    void searchDeals_returnsEmptyListWhenNoDeals() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("[]");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        List<FlightDeal> deals = flightApiClient.searchDeals("Tokyo");

        assertTrue(deals.isEmpty());
    }

    @Test
    void searchDeals_throwsOnHttpError() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        FlightApiException ex = assertThrows(FlightApiException.class,
                () -> flightApiClient.searchDeals("London"));

        assertEquals("London", ex.getDestination());
        assertEquals("HTTP_ERROR", ex.getErrorType());
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void searchDeals_throwsOnTimeout() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("Connection timed out"));

        FlightApiException ex = assertThrows(FlightApiException.class,
                () -> flightApiClient.searchDeals("Berlin"));

        assertEquals("Berlin", ex.getDestination());
        assertEquals("TIMEOUT", ex.getErrorType());
    }

    @Test
    void searchDeals_throwsOnIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        FlightApiException ex = assertThrows(FlightApiException.class,
                () -> flightApiClient.searchDeals("Rome"));

        assertEquals("Rome", ex.getDestination());
        assertEquals("IO_ERROR", ex.getErrorType());
    }

    @Test
    void searchDeals_throwsOnMalformedJson() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("not valid json");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        FlightApiException ex = assertThrows(FlightApiException.class,
                () -> flightApiClient.searchDeals("Madrid"));

        assertEquals("PARSE_ERROR", ex.getErrorType());
    }
}
