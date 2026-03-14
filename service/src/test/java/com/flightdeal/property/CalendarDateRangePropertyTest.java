// Feature: flight-deal-notifier, Property 8: Calendar date range derived from flights
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightdeal.generated.model.TimeWindow;
import com.flightdeal.proxy.CalendarApiException;
import com.flightdeal.proxy.GoogleCalendarClient;
import com.flightdeal.service.CalendarService;
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

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Property(tries = 100)
  void calendarQuerySpansMinDepartureToMaxArrival(@ForAll("flightList") List<FlightDates> dates)
      throws CalendarApiException {

    GoogleCalendarClient googleCalendarClient = mock(GoogleCalendarClient.class);
    when(googleCalendarClient.getFreeBusyWindows(anyString(), anyString()))
        .thenReturn(
            List.of(TimeWindow.builder().startDate("2025-01-01").endDate("2025-12-31").build()));

    CalendarService calendarService = new CalendarService(googleCalendarClient);

    List<JsonNode> flights =
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

  private static JsonNode createFlight(String depDate, String arrDate) {
    ObjectNode flight = MAPPER.createObjectNode();
    flight.put("price", 200);
    ArrayNode flights = MAPPER.createArrayNode();
    ObjectNode segment = MAPPER.createObjectNode();
    ObjectNode dep = MAPPER.createObjectNode();
    dep.put("id", "JFK");
    dep.put("name", "JFK Airport");
    dep.put("time", depDate + " 10:00");
    segment.set("departure_airport", dep);
    ObjectNode arr = MAPPER.createObjectNode();
    arr.put("id", "CDG");
    arr.put("name", "CDG Airport");
    arr.put("time", arrDate + " 18:00");
    segment.set("arrival_airport", arr);
    segment.put("airline", "TestAir");
    flights.add(segment);
    flight.set("flights", flights);
    return flight;
  }
}
