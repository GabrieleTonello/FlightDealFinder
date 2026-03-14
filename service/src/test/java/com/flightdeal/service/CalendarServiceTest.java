package com.flightdeal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.proxy.CalendarApiException;
import com.flightdeal.proxy.GoogleCalendarClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for CalendarService with JsonObject-based flights. */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

  @Mock private GoogleCalendarClient googleCalendarClient;

  private CalendarService calendarService;

  @BeforeEach
  void setUp() {
    calendarService = new CalendarService(googleCalendarClient);
  }

  static JsonObject flight(String depTime, String arrTime) {
    JsonObject node = new JsonObject();
    node.addProperty("price", 200);
    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();

    JsonObject depAirport = new JsonObject();
    depAirport.addProperty("id", "JFK");
    depAirport.addProperty("name", "JFK Airport");
    depAirport.addProperty("time", depTime);
    segment.add("departure_airport", depAirport);

    JsonObject arrAirport = new JsonObject();
    arrAirport.addProperty("id", "CDG");
    arrAirport.addProperty("name", "CDG Airport");
    arrAirport.addProperty("time", arrTime);
    segment.add("arrival_airport", arrAirport);

    segment.addProperty("airline", "TestAir");
    flights.add(segment);
    node.add("flights", flights);
    return node;
  }

  @Test
  void lookupFreeWindows_successfulLookup_returnsFreeWindows() throws CalendarApiException {
    List<JsonObject> flights = List.of(flight("2025-07-01 10:00", "2025-07-10 18:00"));
    List<TimeWindow> expectedWindows =
        List.of(
            TimeWindow.builder().startDate("2025-07-01").endDate("2025-07-05").build(),
            TimeWindow.builder().startDate("2025-07-08").endDate("2025-07-10").build());

    when(googleCalendarClient.getFreeBusyWindows("2025-07-01", "2025-07-10"))
        .thenReturn(expectedWindows);

    List<TimeWindow> result = calendarService.lookupFreeWindows(flights);

    assertEquals(expectedWindows, result);
    assertEquals(2, result.size());
  }

  @Test
  void lookupFreeWindows_multipleFlights_computesCorrectDateRange() throws CalendarApiException {
    List<JsonObject> flights =
        List.of(
            flight("2025-07-15 10:00", "2025-07-20 18:00"),
            flight("2025-07-01 10:00", "2025-07-25 18:00"),
            flight("2025-07-10 10:00", "2025-07-18 18:00"));

    when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString())).thenReturn(List.of());

    calendarService.lookupFreeWindows(flights);

    ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> endCaptor = ArgumentCaptor.forClass(String.class);
    verify(googleCalendarClient).getFreeBusyWindows(startCaptor.capture(), endCaptor.capture());

    assertEquals("2025-07-01", startCaptor.getValue());
    assertEquals("2025-07-25", endCaptor.getValue());
  }

  @Test
  void lookupFreeWindows_nullFlights_returnsEmpty() {
    List<TimeWindow> result = calendarService.lookupFreeWindows(null);
    assertTrue(result.isEmpty());
    verifyNoInteractions(googleCalendarClient);
  }

  @Test
  void lookupFreeWindows_emptyFlights_returnsEmpty() {
    List<TimeWindow> result = calendarService.lookupFreeWindows(Collections.emptyList());
    assertTrue(result.isEmpty());
    verifyNoInteractions(googleCalendarClient);
  }

  @Test
  void lookupFreeWindows_calendarApiException_wrappedAsRuntimeException()
      throws CalendarApiException {
    List<JsonObject> flights = List.of(flight("2025-07-01 10:00", "2025-07-10 18:00"));

    CalendarApiException apiException =
        new CalendarApiException("Service unavailable", "HTTP_ERROR");
    when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString())).thenThrow(apiException);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> calendarService.lookupFreeWindows(flights));

    assertTrue(thrown.getMessage().contains("Calendar lookup failed"));
    assertSame(apiException, thrown.getCause());
  }
}
