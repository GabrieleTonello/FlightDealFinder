// Feature: flight-deal-notifier, Property 8: Calendar date range derived from deals
package com.flightdeal.property;

import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.proxy.CalendarApiException;
import com.flightdeal.proxy.GoogleCalendarClient;
import com.flightdeal.service.CalendarService;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 8: For any non-empty list of deals, the CalendarService queries with
 * min(departureDate) to max(returnDate).
 *
 * Validates: Requirements 7.1
 */
class CalendarDateRangePropertyTest {

    @Property(tries = 100)
    void calendarQuerySpansMinDepartureToMaxReturn(
            @ForAll("dealList") List<DealDates> dealDates) throws CalendarApiException {

        GoogleCalendarClient googleCalendarClient = mock(GoogleCalendarClient.class);
        when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString()))
                .thenReturn(List.of(TimeWindow.builder()
                        .startDate("2025-01-01")
                        .endDate("2025-12-31")
                        .build()));

        CalendarService calendarService = new CalendarService(googleCalendarClient);

        List<FlightDeal> deals = dealDates.stream()
                .map(dd -> FlightDeal.builder()
                        .destination("TestDest")
                        .price(new BigDecimal("200.00"))
                        .departureDate(dd.departure)
                        .returnDate(dd.returnDate)
                        .airline("TestAir")
                        .build())
                .collect(Collectors.toList());

        calendarService.lookupFreeWindows(deals);

        // Compute expected min departure and max return
        String expectedStart = dealDates.stream()
                .map(dd -> dd.departure)
                .min(String::compareTo)
                .orElseThrow();
        String expectedEnd = dealDates.stream()
                .map(dd -> dd.returnDate)
                .max(String::compareTo)
                .orElseThrow();

        ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> endCaptor = ArgumentCaptor.forClass(String.class);
        verify(googleCalendarClient).getFreeBusyWindows(startCaptor.capture(), endCaptor.capture());

        assertEquals(expectedStart, startCaptor.getValue(),
                "Calendar query start should be min departure date");
        assertEquals(expectedEnd, endCaptor.getValue(),
                "Calendar query end should be max return date");
    }

    @Provide
    Arbitrary<List<DealDates>> dealList() {
        Arbitrary<DealDates> dealArb = Arbitraries.integers().between(0, 179)
                .flatMap(depOffset -> Arbitraries.integers().between(1, 30)
                        .map(tripLength -> {
                            LocalDate dep = LocalDate.of(2025, 1, 1).plusDays(depOffset);
                            LocalDate ret = dep.plusDays(tripLength);
                            return new DealDates(dep.toString(), ret.toString());
                        }));
        return dealArb.list().ofMinSize(1).ofMaxSize(8);
    }

    record DealDates(String departure, String returnDate) {}
}
