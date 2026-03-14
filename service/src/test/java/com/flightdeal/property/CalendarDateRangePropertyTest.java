// Feature: flight-deal-notifier, Property 8: Calendar date range derived from flights
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.proxy.CalendarApiException;
import com.flightdeal.proxy.GoogleCalendarClient;
import com.flightdeal.service.CalendarService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

/**
 * Property 8: For any non-empty list of flights, the CalendarService queries with
 * min(departureDate) to max(arrivalDate).
 */
class CalendarDateRangePropertyTest {

  @Property(tries = 100)
  void calendarQuerySpansMinDepartureToMaxArrival(@ForAll("flightList") List<FlightDates> dates)
      throws CalendarApiException {

    GoogleCalendarClient googleCalendarClient = mock(GoogleCalendarClient.class);
    when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString()))
        .thenReturn(
            List.of(TimeWindow.builder().startDate("2025-01-01").endDate("2025-12-31").build()));

    CalendarService calendarService = new CalendarService(googleCalendarClient);

    List<JsonObject> flights =
        dates.stream()
            .map(dd -> createFlight(dd.departure, dd.arrival))
            .collect(Collectors.toList());

    calendarService.lookupFreeWindows(flights);

    String expectedStart =
        dates.stream().map(dd -> dd.departure).min(String::compareTo).orElseThrow();
    String expectedEnd = dates.stream().map(dd -> dd.arrival).max(String::compareTo).orElseThrow();

    ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> endCaptor = ArgumentCaptor.forClass(String.class);
    verify(googleCalendarClient).getFreeBusyWindows(startCaptor.capture(), endCaptor.capture());

    assertEquals(expectedStart, startCaptor.getValue());
    assertEquals(expectedEnd, endCaptor.getValue());
  }

  @Provide
  Arbitrary<List<FlightDates>> flightList() {
    Arbitrary<FlightDates> flightArb =
        Arbitraries.integers()
            .between(0, 179)
            .flatMap(
                depOffset ->
                    Arbitraries.integers()
                        .between(1, 30)
                        .map(
                            tripLength -> {
                              LocalDate dep = LocalDate.of(2025, 1, 1).plusDays(depOffset);
                              LocalDate arr = dep.plusDays(tripLength);
                              return new FlightDates(dep.toString(), arr.toString());
                            }));
    return flightArb.list().ofMinSize(1).ofMaxSize(8);
  }

  record FlightDates(String departure, String arrival) {}

  private static JsonObject createFlight(String depDate, String arrDate) {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", 200);
    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();
    JsonObject dep = new JsonObject();
    dep.addProperty("id", "JFK");
    dep.addProperty("name", "JFK Airport");
    dep.addProperty("time", depDate + " 10:00");
    segment.add("departure_airport", dep);
    JsonObject arr = new JsonObject();
    arr.addProperty("id", "CDG");
    arr.addProperty("name", "CDG Airport");
    arr.addProperty("time", arrDate + " 18:00");
    segment.add("arrival_airport", arr);
    segment.addProperty("airline", "TestAir");
    flights.add(segment);
    flight.add("flights", flights);
    return flight;
  }
}
