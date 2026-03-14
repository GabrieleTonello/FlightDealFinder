package com.flightdeal.service;

import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
import com.flightdeal.generated.model.TimeWindow;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Compares flight deals against free calendar windows and returns matching deals sorted by price
 * ascending.
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
   * Matches flight deals against free calendar windows.
   *
   * @param flights the available flight deals
   * @param freeWindows the user's free calendar windows
   * @return matched flights sorted by price ascending, or empty list if none match
   */
  public List<FlightDeal> matchDeals(List<FlightDeal> flights, List<TimeWindow> freeWindows) {
    if (flights == null || flights.isEmpty() || freeWindows == null || freeWindows.isEmpty()) {
      log.info(
          "No matches possible: flights={}, freeWindows={}",
          flights == null ? 0 : flights.size(),
          freeWindows == null ? 0 : freeWindows.size());
      return List.of();
    }

    List<FlightDeal> matched =
        flights.stream()
            .filter(deal -> fitsAnyWindow(deal, freeWindows))
            .sorted(Comparator.comparingInt(FlightDeal::getPrice))
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

  private boolean fitsAnyWindow(FlightDeal deal, List<TimeWindow> windows) {
    return windows.stream().anyMatch(window -> fitsWindow(deal, window));
  }

  private boolean fitsWindow(FlightDeal deal, TimeWindow window) {
    String departureDate = extractDepartureDate(deal);
    String arrivalDate = extractArrivalDate(deal);

    if (departureDate == null || arrivalDate == null) {
      return false;
    }

    return departureDate.compareTo(window.getStartDate()) >= 0
        && arrivalDate.compareTo(window.getEndDate()) <= 0;
  }

  private String extractDepartureDate(FlightDeal deal) {
    List<FlightSegment> segments = deal.getFlights();
    if (segments == null || segments.isEmpty()) {
      return null;
    }
    Airport depAirport = segments.get(0).getDepartureAirport();
    if (depAirport == null) {
      return null;
    }
    return extractDatePart(depAirport.getTime());
  }

  private String extractArrivalDate(FlightDeal deal) {
    List<FlightSegment> segments = deal.getFlights();
    if (segments == null || segments.isEmpty()) {
      return null;
    }
    Airport arrAirport = segments.get(segments.size() - 1).getArrivalAirport();
    if (arrAirport == null) {
      return null;
    }
    return extractDatePart(arrAirport.getTime());
  }

  /** Extracts the date part (YYYY-MM-DD) from a datetime string like "2025-07-05 10:30". */
  private String extractDatePart(String dateTime) {
    if (dateTime == null || dateTime.length() < 10) {
      return null;
    }
    return dateTime.substring(0, 10);
  }
}
