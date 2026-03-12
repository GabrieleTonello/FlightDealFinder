package com.flightdeal.proxy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightdeal.generated.model.FlightDeal;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Proxy client for querying an external flight API for deals per destination.
 * Designed for constructor injection so it can be mocked in tests.
 */
public class FlightApiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    /**
     * Creates a FlightApiClient with the given dependencies.
     *
     * @param httpClient the HTTP client to use for requests
     * @param baseUrl    the base URL of the external flight API
     * @param apiKey     the API key for authentication
     * @param timeout    the request timeout duration
     */
    public FlightApiClient(HttpClient httpClient, String baseUrl, String apiKey, Duration timeout) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Queries the external flight API for deals for a specific destination.
     *
     * @param destination the destination to search for deals
     * @return a list of flight deals for the destination
     * @throws FlightApiException if the API returns an error or the request times out
     */
    public List<FlightDeal> searchDeals(String destination) throws FlightApiException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/flights?destination=" + destination))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FlightApiException(
                        destination,
                        "API returned HTTP " + response.statusCode(),
                        "HTTP_ERROR"
                );
            }

            return parseDeals(response.body());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new FlightApiException(destination, "Request timed out: " + e.getMessage(), "TIMEOUT");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new FlightApiException(destination, "Request failed: " + e.getMessage(), "IO_ERROR");
        }
    }

    private List<FlightDeal> parseDeals(String responseBody) throws FlightApiException {
        try {
            List<Map<String, Object>> rawDeals = objectMapper.readValue(
                    responseBody, new TypeReference<>() {}
            );

            return rawDeals.stream()
                    .map(this::toFlightDeal)
                    .toList();
        } catch (Exception e) {
            throw new FlightApiException("unknown", "Failed to parse response: " + e.getMessage(), "PARSE_ERROR");
        }
    }

    private FlightDeal toFlightDeal(Map<String, Object> raw) {
        return FlightDeal.builder()
                .destination((String) raw.get("destination"))
                .price(new BigDecimal(raw.get("price").toString()))
                .departureDate((String) raw.get("departureDate"))
                .returnDate((String) raw.get("returnDate"))
                .airline((String) raw.get("airline"))
                .build();
    }
}
