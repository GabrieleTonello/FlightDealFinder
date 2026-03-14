package com.flightdeal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.proxy.CalendarApiException;
import com.flightdeal.proxy.GoogleCalendarClient;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that retrieves free calendar windows for a set of flight deals (JsonNode). Computes the
 * date range from the earliest departure to the latest arrival across all deals, queries Google
 * Calendar, and returns free time windows.
 *
 * <p>CalendarApiException is caught, logged, and rethrown as a RuntimeException so that Step
 * Functions retry policies can handle transient failures.
 */
@Slf4j
@Singleton
public class CalendarService {

  private final GoogleCalendarClient googleCalendarClient;

  @Inject
  public CalendarService(GoogleCalendarClient googleCalendarClient) {
    this.googleCalendarClient = googleCalendarClient;
  }

  /**
   * Looks up free calendar windows for the date range spanned by the given flight JsonNodes.
   *
   * @param flights the list of flight deal JsonNodes to derive the date range from
   * @return list of free time windows from the user's calendar
   * @throws RuntimeException wrapping CalendarApiException on API failure
   */
  public List<TimeWindow> lookupFreeWindows(List<JsonNode> flights) {
    if (flights == null || flights.isEmpty()) {
      log.info("No flights provided, returning empty free windows");
      return List.of();
    }

    String earliestDeparture =
        flights.stream()
            .map(this::extractDepartureDate)
            .filter(Objects::nonNull)
            .min(String::compareTo)
            .orElse(null);

    String latestArrival =
        flights.stream()
            .map(this::extractArrivalDate)
            .filter(Objects::nonNull)
            .max(String::compareTo)
            .orElse(null);

    if (earliestDeparture == null || latestArrival == null) {
      log.warn("Could not extract date range from flights");
      return List.of();
    }

    log.info(
        "Looking up calendar free windows from {} to {} for {} flights",
        earliestDeparture,
        latestArrival,
        flights.size());

    try {
      List<TimeWindow> freeWindows =
          googleCalendarClient.getFreeBusyWindows(earliestDeparture, latestArrival);
      log.info("Found {} free windows", freeWindows.size());
      return freeWindows;
    } catch (CalendarApiException e) {
      log.error("Calendar API failed [{}]: {}", e.getErrorType(), e.getMessage(), e);
      throw new RuntimeException("Calendar lookup failed: " + e.getMessage(), e);
    }
  }

  private String extractDepartureDate(JsonNode flight) {
    JsonNode flightsArray = flight.path("flights");
    if (!flightsArray.isArray() || flightsArray.isEmpty()) {
      return null;
    }
    String time = flightsArray.get(0).path("departure_airport").path("time").asText(null);
    return extractDatePart(time);
  }

  private String extractArrivalDate(JsonNode flight) {
    JsonNode flightsArray = flight.path("flights");
    if (!flightsArray.isArray() || flightsArray.isEmpty()) {
      return null;
    }
    String time =
        flightsArray.get(flightsArray.size() - 1).path("arrival_airport").path("time").asText(null);
    return extractDatePart(time);
  }

  private String extractDatePart(String dateTime) {
    if (dateTime == null || dateTime.length() < 10) {
      return null;
    }
    return dateTime.substring(0, 10);
  }
}
