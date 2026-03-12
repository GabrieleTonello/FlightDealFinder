package com.flightdeal.service;

import com.flightdeal.generated.model.FlightDeal;
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
 * <p>A deal matches a window when the deal's departure date is on or after the window start date
 * AND the deal's return date is on or before the window end date (ISO-8601 string comparison).
 */
@Slf4j
@Singleton
public class FlightMatcher {

  @Inject
  public FlightMatcher() {}

  /**
   * Matches flight deals against free calendar windows.
   *
   * @param deals the available flight deals
   * @param freeWindows the user's free calendar windows
   * @return matched deals sorted by price ascending, or empty list if none match
   */
  public List<FlightDeal> matchDeals(List<FlightDeal> deals, List<TimeWindow> freeWindows) {
    if (deals == null || deals.isEmpty() || freeWindows == null || freeWindows.isEmpty()) {
      log.info(
          "No matches possible: deals={}, freeWindows={}",
          deals == null ? 0 : deals.size(),
          freeWindows == null ? 0 : freeWindows.size());
      return List.of();
    }

    List<FlightDeal> matched =
        deals.stream()
            .filter(deal -> fitsAnyWindow(deal, freeWindows))
            .sorted(Comparator.comparing(FlightDeal::getPrice))
            .toList();

    if (matched.isEmpty()) {
      log.info(
          "No deals match any free calendar window out of {} deals and {} windows",
          deals.size(),
          freeWindows.size());
    } else {
      log.info(
          "Matched {} deals out of {} against {} free windows",
          matched.size(),
          deals.size(),
          freeWindows.size());
    }

    return matched;
  }

  private boolean fitsAnyWindow(FlightDeal deal, List<TimeWindow> windows) {
    return windows.stream().anyMatch(window -> fitsWindow(deal, window));
  }

  private boolean fitsWindow(FlightDeal deal, TimeWindow window) {
    return deal.getDepartureDate().compareTo(window.getStartDate()) >= 0
        && deal.getReturnDate().compareTo(window.getEndDate()) <= 0;
  }
}
