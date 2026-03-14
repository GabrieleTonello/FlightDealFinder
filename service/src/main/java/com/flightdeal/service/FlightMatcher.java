package com.flightdeal.service;

import com.flightdeal.generated.model.TimeWindow;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Compares flight deals (as JsonObject objects from SerpApi) against free calendar windows and
 * returns matching deals sorted by price ascending.
 *
 * <p>A deal matches a window when the first segment's departure time date is on or after the window
 * start date AND the last segment's arrival time date is on or before the window end date.
 */
@Slf4j
@Singleton
public class FlightMatcher {

  @Inject
  public FlightMatcher() {}

  /**
   * Matches flight deal JsonObjects against free calendar windows.
   *
   * @param flights the available flight deal JsonObjects from SerpApi
   * @param freeWindows the user's free calendar windows
   * @return matched flights sorted by price ascending, or empty list if none match
   */
  public List<JsonObject> matchDeals(List<JsonObject> flights, List<TimeWindow> freeWindows) {
    if (flights == null || flights.isEmpty() || freeWindows == null || freeWindows.isEmpty()) {
      log.info(
          "No matches possible: flights={}, freeWindows={}",
          flights == null ? 0 : flights.size(),
          freeWindows == null ? 0 : freeWindows.size());
      return List.of();
    }

    List<JsonObject> matched =
        flights.stream()
            .filter(flight -> fitsAnyWindow(flight, freeWindows))
            .sorted(
                Comparator.comparingInt(
                    f -> f.has("price") ? f.get("price").getAsInt() : Integer.MAX_VALUE))
            .toList();

    if (matched.isEmpty()) {
      log.info(
          "No flights match any free calendar window out of {} flights and {} windows",
          flights.size(),
          freeWindows.size());
    } else {
      log.info(
          "Matched {} flights out of {} against {} free windows",
          matched.size(),
          flights.size(),
          freeWindows.size());
    }

    return matched;
  }

  private boolean fitsAnyWindow(JsonObject flight, List<TimeWindow> windows) {
    return windows.stream().anyMatch(window -> fitsWindow(flight, window));
  }

  private boolean fitsWindow(JsonObject flight, TimeWindow window) {
    String departureDate = extractDepartureDate(flight);
    String arrivalDate = extractArrivalDate(flight);

    if (departureDate == null || arrivalDate == null) {
      return false;
    }

    return departureDate.compareTo(window.getStartDate()) >= 0
        && arrivalDate.compareTo(window.getEndDate()) <= 0;
  }

  private String extractDepartureDate(JsonObject flight) {
    if (!flight.has("flights") || !flight.get("flights").isJsonArray()) {
      return null;
    }
    JsonArray flightsArray = flight.getAsJsonArray("flights");
    if (flightsArray.size() == 0) {
      return null;
    }
    JsonObject firstSegment = flightsArray.get(0).getAsJsonObject();
    if (!firstSegment.has("departure_airport")) {
      return null;
    }
    JsonObject depAirport = firstSegment.getAsJsonObject("departure_airport");
    String time = depAirport.has("time") ? depAirport.get("time").getAsString() : null;
    return extractDatePart(time);
  }

  private String extractArrivalDate(JsonObject flight) {
    if (!flight.has("flights") || !flight.get("flights").isJsonArray()) {
      return null;
    }
    JsonArray flightsArray = flight.getAsJsonArray("flights");
    if (flightsArray.size() == 0) {
      return null;
    }
    JsonObject lastSegment = flightsArray.get(flightsArray.size() - 1).getAsJsonObject();
    if (!lastSegment.has("arrival_airport")) {
      return null;
    }
    JsonObject arrAirport = lastSegment.getAsJsonObject("arrival_airport");
    String time = arrAirport.has("time") ? arrAirport.get("time").getAsString() : null;
    return extractDatePart(time);
  }

  /** Extracts the date part (YYYY-MM-DD) from a datetime string like "2025-07-05 10:30". */
  private String extractDatePart(String dateTime) {
    if (dateTime == null || dateTime.length() < 10) {
      return null;
    }
    return dateTime.substring(0, 10);
  }
}
