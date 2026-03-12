package com.flightdeal.proxy;

import com.flightdeal.generated.model.TimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleCalendarClient.
 * Validates: Requirements 7.1, 17.1, 17.5
 */
@ExtendWith(MockitoExtension.class)
class GoogleCalendarClientTest {

    private static final String CALENDAR_ID = "test@gmail.com";
    private static final String ACCESS_TOKEN = "test-token";

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GoogleCalendarClient client;

    @BeforeEach
    void setUp() {
        client = new GoogleCalendarClient(httpClient, CALENDAR_ID, ACCESS_TOKEN);
    }

    @SuppressWarnings("unchecked")
    private void mockResponse(int statusCode, String body) throws IOException, InterruptedException {
        when(httpResponse.statusCode()).thenReturn(statusCode);
        when(httpResponse.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    // ---- Successful response with busy periods returns computed free windows ----

    @Test
    void getFreeBusyWindows_withBusyPeriods_returnsComputedFreeWindows() throws Exception {
        String responseBody = """
                {
                  "calendars": {
                    "test@gmail.com": {
                      "busy": [
                        {"start": "2025-07-05T00:00:00Z", "end": "2025-07-08T00:00:00Z"}
                      ]
                    }
                  }
                }
                """;
        mockResponse(200, responseBody);

        List<TimeWindow> windows = client.getFreeBusyWindows("2025-07-01", "2025-07-15");

        assertFalse(windows.isEmpty());
        // Free window before busy: 2025-07-01 to 2025-07-04
        assertEquals("2025-07-01", windows.get(0).getStartDate());
        assertEquals("2025-07-04", windows.get(0).getEndDate());
        // Free window after busy: 2025-07-08 to 2025-07-15
        assertEquals("2025-07-08", windows.get(1).getStartDate());
        assertEquals("2025-07-15", windows.get(1).getEndDate());
    }

    // ---- Empty busy periods returns full range as free ----

    @Test
    void getFreeBusyWindows_emptyBusyPeriods_returnsFullRangeAsFree() throws Exception {
        String responseBody = """
                {
                  "calendars": {
                    "test@gmail.com": {
                      "busy": []
                    }
                  }
                }
                """;
        mockResponse(200, responseBody);

        List<TimeWindow> windows = client.getFreeBusyWindows("2025-07-01", "2025-07-15");

        assertEquals(1, windows.size());
        assertEquals("2025-07-01", windows.get(0).getStartDate());
        assertEquals("2025-07-15", windows.get(0).getEndDate());
    }

    // ---- Non-200 status throws CalendarApiException with HTTP_ERROR ----

    @SuppressWarnings("unchecked")
    @Test
    void getFreeBusyWindows_non200Status_throwsCalendarApiExceptionWithHttpError() throws Exception {
        when(httpResponse.statusCode()).thenReturn(403);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        CalendarApiException ex = assertThrows(CalendarApiException.class,
                () -> client.getFreeBusyWindows("2025-07-01", "2025-07-15"));

        assertEquals("HTTP_ERROR", ex.getErrorType());
        assertTrue(ex.getMessage().contains("403"));
    }

    // ---- Timeout throws CalendarApiException with TIMEOUT ----

    @SuppressWarnings("unchecked")
    @Test
    void getFreeBusyWindows_timeout_throwsCalendarApiExceptionWithTimeout() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("Connection timed out"));

        CalendarApiException ex = assertThrows(CalendarApiException.class,
                () -> client.getFreeBusyWindows("2025-07-01", "2025-07-15"));

        assertEquals("TIMEOUT", ex.getErrorType());
    }

    // ---- IOException throws CalendarApiException with IO_ERROR ----

    @SuppressWarnings("unchecked")
    @Test
    void getFreeBusyWindows_ioException_throwsCalendarApiExceptionWithIoError() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        CalendarApiException ex = assertThrows(CalendarApiException.class,
                () -> client.getFreeBusyWindows("2025-07-01", "2025-07-15"));

        assertEquals("IO_ERROR", ex.getErrorType());
    }

    // ---- Malformed JSON throws CalendarApiException with PARSE_ERROR ----

    @Test
    void getFreeBusyWindows_malformedJson_throwsCalendarApiExceptionWithParseError() throws Exception {
        mockResponse(200, "not valid json {{{");

        CalendarApiException ex = assertThrows(CalendarApiException.class,
                () -> client.getFreeBusyWindows("2025-07-01", "2025-07-15"));

        assertEquals("PARSE_ERROR", ex.getErrorType());
    }

    // ---- No calendar data for calendarId returns full range ----

    @Test
    void getFreeBusyWindows_noCalendarDataForId_returnsFullRange() throws Exception {
        String responseBody = """
                {
                  "calendars": {
                    "other@gmail.com": {
                      "busy": [
                        {"start": "2025-07-05T00:00:00Z", "end": "2025-07-08T00:00:00Z"}
                      ]
                    }
                  }
                }
                """;
        mockResponse(200, responseBody);

        List<TimeWindow> windows = client.getFreeBusyWindows("2025-07-01", "2025-07-15");

        assertEquals(1, windows.size());
        assertEquals("2025-07-01", windows.get(0).getStartDate());
        assertEquals("2025-07-15", windows.get(0).getEndDate());
    }
}
