// Feature: flight-deal-notifier, Property 12: Notification email contains all deal fields
package com.flightdeal.property;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flightdeal.service.NotificationService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

/**
 * Property 12: For any non-empty list of matched flights, the email body contains price, airline,
 * airports for every flight.
 */
class EmailCompletenessPropertyTest {

  @Property(tries = 100)
  void emailBodyContainsAllFlightFields(@ForAll("flightList") List<FlightInput> flightInputs) {
    SesClient sesClient = mock(SesClient.class);
    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("test-msg-id").build());

    NotificationService service =
        new NotificationService(sesClient, "sender@test.com", "recipient@test.com");

    List<JsonObject> flights =
        flightInputs.stream()
            .map(input -> createFlight(input.price, input.airline, input.depName, input.arrName))
            .collect(Collectors.toList());

    service.sendDealNotification(flights);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String emailBody = captor.getValue().message().body().text().data();

    for (FlightInput input : flightInputs) {
      assertTrue(
          emailBody.contains(String.valueOf(input.price)),
          "Email should contain price: " + input.price);
      assertTrue(
          emailBody.contains(input.airline), "Email should contain airline: " + input.airline);
      assertTrue(
          emailBody.contains(input.depName),
          "Email should contain departure airport: " + input.depName);
      assertTrue(
          emailBody.contains(input.arrName),
          "Email should contain arrival airport: " + input.arrName);
    }
  }

  @Provide
  Arbitrary<List<FlightInput>> flightList() {
    Arbitrary<FlightInput> flightArb =
        Arbitraries.integers()
            .between(50, 2000)
            .flatMap(
                price ->
                    Arbitraries.of("AirFrance", "Delta", "ANA", "Lufthansa", "BA", "JAL")
                        .flatMap(
                            airline ->
                                Arbitraries.of("JFK Airport", "LAX Airport", "LHR Airport")
                                    .flatMap(
                                        depName ->
                                            Arbitraries.of(
                                                    "CDG Airport", "NRT Airport", "FRA Airport")
                                                .map(
                                                    arrName ->
                                                        new FlightInput(
                                                            price, airline, depName, arrName)))));
    return flightArb.list().ofMinSize(1).ofMaxSize(5);
  }

  record FlightInput(int price, String airline, String depName, String arrName) {}

  private static JsonObject createFlight(
      int price, String airline, String depName, String arrName) {
    JsonObject flight = new JsonObject();
    flight.addProperty("price", price);
    flight.addProperty("total_duration", 480);
    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();
    JsonObject dep = new JsonObject();
    dep.addProperty("id", "DEP");
    dep.addProperty("name", depName);
    dep.addProperty("time", "2025-07-01 10:00");
    segment.add("departure_airport", dep);
    JsonObject arr = new JsonObject();
    arr.addProperty("id", "ARR");
    arr.addProperty("name", arrName);
    arr.addProperty("time", "2025-07-01 18:00");
    segment.add("arrival_airport", arr);
    segment.addProperty("airline", airline);
    flights.add(segment);
    flight.add("flights", flights);
    return flight;
  }
}
