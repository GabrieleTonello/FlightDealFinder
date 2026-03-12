package com.flightdeal.service;

import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.proxy.CalendarApiException;
import com.flightdeal.proxy.GoogleCalendarClient;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Service that retrieves free calendar windows for a set of flight deals.
 * Computes the date range from the earliest departure to the latest return
 * across all deals, queries Google Calendar, and returns free time windows.
 *
 * CalendarApiException is caught, logged, and rethrown as a RuntimeException
 * so that Step Functions retry policies can handle transient failures.
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
     * Looks up free calendar windows for the date range spanned by the given deals.
     *
     * @param deals the list of flight deals to derive the date range from
     * @return list of free time windows from the user's calendar
     * @throws RuntimeException wrapping CalendarApiException on API failure
     */
    public List<TimeWindow> lookupFreeWindows(List<FlightDeal> deals) {
        if (deals == null || deals.isEmpty()) {
            log.info("No deals provided, returning empty free windows");
            return List.of();
        }

        String earliestDeparture = deals.stream()
                .map(FlightDeal::getDepartureDate)
                .min(String::compareTo)
                .orElseThrow();

        String latestReturn = deals.stream()
                .map(FlightDeal::getReturnDate)
                .max(String::compareTo)
                .orElseThrow();

        log.info("Looking up calendar free windows from {} to {} for {} deals",
                earliestDeparture, latestReturn, deals.size());

        try {
            List<TimeWindow> freeWindows = googleCalendarClient.getFreeBusyWindows(earliestDeparture, latestReturn);
            log.info("Found {} free windows", freeWindows.size());
            return freeWindows;
        } catch (CalendarApiException e) {
            log.error("Calendar API failed [{}]: {}", e.getErrorType(), e.getMessage(), e);
            throw new RuntimeException("Calendar lookup failed: " + e.getMessage(), e);
        }
    }
}
