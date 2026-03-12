package com.flightdeal.proxy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightdeal.generated.model.TimeWindow;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy client for the Google Calendar FreeBusy API. Authenticates via OAuth2 access token and
 * retrieves free/busy windows for a date range. Designed for constructor injection so it can be
 * mocked in tests.
 */
@Slf4j
public class GoogleCalendarClient {

  private static final String FREEBUSY_URL = "https://www.googleapis.com/calendar/v3/freeBusy";
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  private final HttpClient httpClient;
  private final String calendarId;
  private final String accessToken;
  private final ObjectMapper objectMapper;

  /**
   * Creates a GoogleCalendarClient with the given dependencies.
   *
   * @param httpClient the HTTP client to use for requests
   * @param calendarId the Google Calendar ID to query
   * @param accessToken the OAuth2 access token for authentication
   */
  public GoogleCalendarClient(HttpClient httpClient, String calendarId, String accessToken) {
    this.httpClient = httpClient;
    this.calendarId = calendarId;
    this.accessToken = accessToken;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Retrieves free time windows from Google Calendar for the given date range. Calls the FreeBusy
   * API and inverts the busy periods to compute free windows.
   *
   * @param startDate the start of the date range (ISO-8601 date, e.g. "2025-07-01")
   * @param endDate the end of the date range (ISO-8601 date, e.g. "2025-07-15")
   * @return a list of free time windows within the date range
   * @throws CalendarApiException if the API returns an error or the request fails
   */
  public List<TimeWindow> getFreeBusyWindows(String startDate, String endDate)
      throws CalendarApiException {
    String requestBody = buildRequestBody(startDate, endDate);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(FREEBUSY_URL))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .timeout(DEFAULT_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("Google Calendar API returned HTTP {}", response.statusCode());
        throw new CalendarApiException(
            "Google Calendar API returned HTTP " + response.statusCode(), "HTTP_ERROR");
      }

      return parseFreeWindows(response.body(), startDate, endDate);
    } catch (java.net.http.HttpTimeoutException e) {
      log.error("Google Calendar API request timed out", e);
      throw new CalendarApiException("Request timed out: " + e.getMessage(), "TIMEOUT");
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.error("Google Calendar API request failed", e);
      throw new CalendarApiException("Request failed: " + e.getMessage(), "IO_ERROR");
    }
  }

  private String buildRequestBody(String startDate, String endDate) {
    Instant timeMin = LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant timeMax = LocalDate.parse(endDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    return String.format(
        """
                {
                  "timeMin": "%s",
                  "timeMax": "%s",
                  "items": [{"id": "%s"}]
                }
                """,
        timeMin.toString(), timeMax.toString(), calendarId);
  }

  @SuppressWarnings("unchecked")
  private List<TimeWindow> parseFreeWindows(String responseBody, String startDate, String endDate)
      throws CalendarApiException {
    try {
      Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<>() {});

      Map<String, Object> calendars = (Map<String, Object>) response.get("calendars");
      if (calendars == null || !calendars.containsKey(calendarId)) {
        log.warn("No calendar data found for calendarId: {}", calendarId);
        return List.of(TimeWindow.builder().startDate(startDate).endDate(endDate).build());
      }

      Map<String, Object> calendarData = (Map<String, Object>) calendars.get(calendarId);
      List<Map<String, String>> busyPeriods = (List<Map<String, String>>) calendarData.get("busy");

      if (busyPeriods == null || busyPeriods.isEmpty()) {
        log.info("No busy periods found, entire range is free");
        return List.of(TimeWindow.builder().startDate(startDate).endDate(endDate).build());
      }

      return computeFreeWindows(busyPeriods, startDate, endDate);
    } catch (Exception e) {
      log.error("Failed to parse Google Calendar API response", e);
      throw new CalendarApiException("Failed to parse response: " + e.getMessage(), "PARSE_ERROR");
    }
  }

  private List<TimeWindow> computeFreeWindows(
      List<Map<String, String>> busyPeriods, String startDate, String endDate) {
    List<TimeWindow> freeWindows = new ArrayList<>();
    LocalDate rangeStart = LocalDate.parse(startDate);
    LocalDate rangeEnd = LocalDate.parse(endDate);

    // Sort busy periods by start time
    List<Map<String, String>> sorted =
        busyPeriods.stream().sorted((a, b) -> a.get("start").compareTo(b.get("start"))).toList();

    LocalDate currentStart = rangeStart;

    for (Map<String, String> busy : sorted) {
      LocalDate busyStart = Instant.parse(busy.get("start")).atZone(ZoneOffset.UTC).toLocalDate();
      LocalDate busyEnd = Instant.parse(busy.get("end")).atZone(ZoneOffset.UTC).toLocalDate();

      if (currentStart.isBefore(busyStart)) {
        freeWindows.add(
            TimeWindow.builder()
                .startDate(currentStart.toString())
                .endDate(busyStart.minusDays(1).toString())
                .build());
      }

      if (busyEnd.isAfter(currentStart)) {
        currentStart = busyEnd;
      }
    }

    // Add trailing free window after last busy period
    if (currentStart.isBefore(rangeEnd) || currentStart.isEqual(rangeEnd)) {
      freeWindows.add(
          TimeWindow.builder()
              .startDate(currentStart.toString())
              .endDate(rangeEnd.toString())
              .build());
    }

    return freeWindows;
  }
}
