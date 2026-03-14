package com.flightdeal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

/** Unit tests for NotificationService with JsonObject-based flights. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  private static final String SENDER = "sender@example.com";
  private static final String RECIPIENT = "recipient@example.com";

  @Mock private SesClient sesClient;

  private NotificationService notificationService;

  @BeforeEach
  void setUp() {
    notificationService = new NotificationService(sesClient, SENDER, RECIPIENT);
  }

  static JsonObject flight(
      int price,
      String airline,
      String depId,
      String depName,
      String depTime,
      String arrId,
      String arrName,
      String arrTime,
      int totalDuration) {
    JsonObject node = new JsonObject();
    node.addProperty("price", price);
    node.addProperty("total_duration", totalDuration);

    JsonArray flights = new JsonArray();
    JsonObject segment = new JsonObject();

    JsonObject depAirport = new JsonObject();
    depAirport.addProperty("id", depId);
    depAirport.addProperty("name", depName);
    depAirport.addProperty("time", depTime);
    segment.add("departure_airport", depAirport);

    JsonObject arrAirport = new JsonObject();
    arrAirport.addProperty("id", arrId);
    arrAirport.addProperty("name", arrName);
    arrAirport.addProperty("time", arrTime);
    segment.add("arrival_airport", arrAirport);

    segment.addProperty("airline", airline);
    flights.add(segment);
    node.add("flights", flights);
    return node;
  }

  @Test
  void sendDealNotification_success_returnsMessageId() {
    List<JsonObject> flights =
        List.of(
            flight(
                299,
                "AirFrance",
                "JFK",
                "JFK Airport",
                "2025-07-01 10:00",
                "CDG",
                "CDG Airport",
                "2025-07-01 18:00",
                480));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-123").build());

    String messageId = notificationService.sendDealNotification(flights);

    assertEquals("msg-123", messageId);
  }

  @Test
  void sendDealNotification_emailBodyContainsAllFields() {
    JsonObject deal =
        flight(
            499,
            "JAL",
            "LAX",
            "Los Angeles",
            "2025-08-01 09:00",
            "NRT",
            "Narita",
            "2025-08-02 14:00",
            660);

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-456").build());

    notificationService.sendDealNotification(List.of(deal));

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String body = captor.getValue().message().body().text().data();
    assertTrue(body.contains("499"), "Body should contain price");
    assertTrue(body.contains("JAL"), "Body should contain airline");
    assertTrue(body.contains("Los Angeles"), "Body should contain departure airport");
    assertTrue(body.contains("LAX"), "Body should contain departure airport id");
    assertTrue(body.contains("Narita"), "Body should contain arrival airport");
    assertTrue(body.contains("NRT"), "Body should contain arrival airport id");
    assertTrue(body.contains("660"), "Body should contain duration");
  }

  @Test
  void sendDealNotification_multipleDeals_allFormattedInBody() {
    List<JsonObject> flights =
        List.of(
            flight(
                299,
                "AirFrance",
                "JFK",
                "JFK Airport",
                "2025-07-01 10:00",
                "CDG",
                "CDG Airport",
                "2025-07-01 18:00",
                480),
            flight(
                599,
                "ANA",
                "LAX",
                "LAX Airport",
                "2025-08-05 11:00",
                "NRT",
                "NRT Airport",
                "2025-08-06 15:00",
                720));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-789").build());

    notificationService.sendDealNotification(flights);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String body = captor.getValue().message().body().text().data();
    assertTrue(body.contains("AirFrance"));
    assertTrue(body.contains("ANA"));
    assertTrue(body.contains("299"));
    assertTrue(body.contains("599"));
    assertTrue(body.contains("Deal 1:"));
    assertTrue(body.contains("Deal 2:"));
  }

  @Test
  void sendDealNotification_sesThrowsException_wrappedAsRuntimeException() {
    List<JsonObject> flights =
        List.of(
            flight(
                199,
                "Lufthansa",
                "JFK",
                "JFK Airport",
                "2025-09-01 10:00",
                "FRA",
                "Frankfurt",
                "2025-09-01 22:00",
                480));

    SesException sesException =
        (SesException) SesException.builder().message("Service unavailable").build();
    when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(sesException);

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class, () -> notificationService.sendDealNotification(flights));

    assertTrue(thrown.getMessage().contains("Failed to send notification email"));
    assertSame(sesException, thrown.getCause());
  }

  @Test
  void sendDealNotification_singleDeal_subjectLineSingular() {
    List<JsonObject> flights =
        List.of(
            flight(
                350,
                "Alitalia",
                "JFK",
                "JFK Airport",
                "2025-10-01 10:00",
                "FCO",
                "Fiumicino",
                "2025-10-01 22:00",
                600));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-single").build());

    notificationService.sendDealNotification(flights);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String subject = captor.getValue().message().subject().data();
    assertEquals("Flight Deal Alert: 1 matching deal found!", subject);
  }

  @Test
  void sendDealNotification_multipleDeals_subjectLinePlural() {
    List<JsonObject> flights =
        List.of(
            flight(
                299, "AF", "JFK", "JFK", "2025-07-01 10:00", "CDG", "CDG", "2025-07-01 18:00", 480),
            flight(
                599,
                "ANA",
                "LAX",
                "LAX",
                "2025-08-05 11:00",
                "NRT",
                "NRT",
                "2025-08-06 15:00",
                720),
            flight(
                450,
                "BA",
                "LHR",
                "LHR",
                "2025-09-01 08:00",
                "JFK",
                "JFK",
                "2025-09-01 16:00",
                480));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-plural").build());

    notificationService.sendDealNotification(flights);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String subject = captor.getValue().message().subject().data();
    assertEquals("Flight Deal Alert: 3 matching deals found!", subject);
  }

  @Test
  void sendDealNotification_usesCorrectSenderAndRecipient() {
    List<JsonObject> flights =
        List.of(
            flight(
                275,
                "Iberia",
                "JFK",
                "JFK",
                "2025-06-15 10:00",
                "MAD",
                "Madrid",
                "2025-06-15 22:00",
                480));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-addr").build());

    notificationService.sendDealNotification(flights);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    SendEmailRequest request = captor.getValue();
    assertEquals(SENDER, request.source());
    assertTrue(request.destination().toAddresses().contains(RECIPIENT));
  }
}
