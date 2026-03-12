package com.flightdeal.service;

import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.proxy.CalendarApiException;
import com.flightdeal.proxy.GoogleCalendarClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CalendarService.
 * Validates: Requirements 7.1, 7.2, 7.3, 17.1, 17.5
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private GoogleCalendarClient googleCalendarClient;

    private CalendarService calendarService;

    @BeforeEach
    void setUp() {
        calendarService = new CalendarService(googleCalendarClient);
    }

    private FlightDeal deal(String destination, String dep, String ret) {
        return FlightDeal.builder()
                .destination(destination)
                .price(new BigDecimal("199.99"))
                .departureDate(dep)
                .returnDate(ret)
                .airline("TestAir")
                .build();
    }

    // ---- Successful lookup returns free windows ----

    @Test
    void lookupFreeWindows_successfulLookup_returnsFreeWindows() throws CalendarApiException {
        List<FlightDeal> deals = List.of(
                deal("Paris", "2025-07-01", "2025-07-10")
        );
        List<TimeWindow> expectedWindows = List.of(
                TimeWindow.builder().startDate("2025-07-01").endDate("2025-07-05").build(),
                TimeWindow.builder().startDate("2025-07-08").endDate("2025-07-10").build()
        );

        when(googleCalendarClient.getFreeBusyWindows("2025-07-01", "2025-07-10"))
                .thenReturn(expectedWindows);

        List<TimeWindow> result = calendarService.lookupFreeWindows(deals);

        assertEquals(expectedWindows, result);
        assertEquals(2, result.size());
        verify(googleCalendarClient).getFreeBusyWindows("2025-07-01", "2025-07-10");
    }

    // ---- Date range computed from earliest departure to latest return ----

    @Test
    void lookupFreeWindows_multipleDeals_computesCorrectDateRange() throws CalendarApiException {
        List<FlightDeal> deals = List.of(
                deal("Paris", "2025-07-15", "2025-07-20"),
                deal("Tokyo", "2025-07-01", "2025-07-25"),
                deal("London", "2025-07-10", "2025-07-18")
        );

        when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString()))
                .thenReturn(List.of());

        calendarService.lookupFreeWindows(deals);

        ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> endCaptor = ArgumentCaptor.forClass(String.class);
        verify(googleCalendarClient).getFreeBusyWindows(startCaptor.capture(), endCaptor.capture());

        assertEquals("2025-07-01", startCaptor.getValue(), "Start date should be earliest departure");
        assertEquals("2025-07-25", endCaptor.getValue(), "End date should be latest return");
    }

    @Test
    void lookupFreeWindows_singleDeal_usesItsDatesAsRange() throws CalendarApiException {
        List<FlightDeal> deals = List.of(
                deal("Berlin", "2025-08-05", "2025-08-12")
        );

        when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString()))
                .thenReturn(List.of());

        calendarService.lookupFreeWindows(deals);

        ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> endCaptor = ArgumentCaptor.forClass(String.class);
        verify(googleCalendarClient).getFreeBusyWindows(startCaptor.capture(), endCaptor.capture());

        assertEquals("2025-08-05", startCaptor.getValue());
        assertEquals("2025-08-12", endCaptor.getValue());
    }

    // ---- Null deals list returns empty ----

    @Test
    void lookupFreeWindows_nullDeals_returnsEmpty() throws CalendarApiException {
        List<TimeWindow> result = calendarService.lookupFreeWindows(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(googleCalendarClient);
    }

    // ---- Empty deals list returns empty ----

    @Test
    void lookupFreeWindows_emptyDeals_returnsEmpty() throws CalendarApiException {
        List<TimeWindow> result = calendarService.lookupFreeWindows(Collections.emptyList());

        assertTrue(result.isEmpty());
        verifyNoInteractions(googleCalendarClient);
    }

    // ---- CalendarApiException wrapped as RuntimeException ----

    @Test
    void lookupFreeWindows_calendarApiException_wrappedAsRuntimeException() throws CalendarApiException {
        List<FlightDeal> deals = List.of(
                deal("Paris", "2025-07-01", "2025-07-10")
        );

        CalendarApiException apiException = new CalendarApiException("Service unavailable", "HTTP_ERROR");
        when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString()))
                .thenThrow(apiException);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> calendarService.lookupFreeWindows(deals));

        assertTrue(thrown.getMessage().contains("Calendar lookup failed"));
        assertSame(apiException, thrown.getCause());
    }

    @Test
    void lookupFreeWindows_calendarApiTimeout_wrappedAsRuntimeException() throws CalendarApiException {
        List<FlightDeal> deals = List.of(
                deal("Tokyo", "2025-09-01", "2025-09-10")
        );

        CalendarApiException timeoutException = new CalendarApiException("Request timed out", "TIMEOUT");
        when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString()))
                .thenThrow(timeoutException);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> calendarService.lookupFreeWindows(deals));

        assertTrue(thrown.getMessage().contains("Calendar lookup failed"));
        assertSame(timeoutException, thrown.getCause());
    }
}
