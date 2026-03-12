package com.flightdeal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flightdeal.generated.model.FlightDeal;
import java.math.BigDecimal;
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

/** Unit tests for NotificationService. Validates: Requirements 9.1, 9.2, 9.3, 17.1, 17.5 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private SesClient sesClient;

  private NotificationService notificationService;

  private static final String SENDER = "sender@example.com";
  private static final String RECIPIENT = "recipient@example.com";

  @BeforeEach
  void setUp() {
    notificationService = new NotificationService(sesClient, SENDER, RECIPIENT);
  }

  private FlightDeal deal(
      String destination, String price, String dep, String ret, String airline) {
    return FlightDeal.builder()
        .destination(destination)
        .price(new BigDecimal(price))
        .departureDate(dep)
        .returnDate(ret)
        .airline(airline)
        .build();
  }

  // ---- Successful email send returns messageId ----

  @Test
  void sendDealNotification_success_returnsMessageId() {
    List<FlightDeal> deals =
        List.of(deal("Paris", "299.99", "2025-07-01", "2025-07-10", "AirFrance"));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-123").build());

    String messageId = notificationService.sendDealNotification(deals);

    assertEquals("msg-123", messageId);
    verify(sesClient).sendEmail(any(SendEmailRequest.class));
  }

  // ---- Email body contains all deal fields ----

  @Test
  void sendDealNotification_emailBodyContainsAllDealFields() {
    FlightDeal deal = deal("Tokyo", "499.50", "2025-08-01", "2025-08-10", "JAL");
    List<FlightDeal> deals = List.of(deal);

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-456").build());

    notificationService.sendDealNotification(deals);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String body = captor.getValue().message().body().text().data();
    assertTrue(body.contains("Tokyo"), "Body should contain destination");
    assertTrue(body.contains("499.50"), "Body should contain price");
    assertTrue(body.contains("2025-08-01"), "Body should contain departure date");
    assertTrue(body.contains("2025-08-10"), "Body should contain return date");
    assertTrue(body.contains("JAL"), "Body should contain airline");
  }

  // ---- Multiple deals formatted correctly ----

  @Test
  void sendDealNotification_multipleDeals_allFormattedInBody() {
    List<FlightDeal> deals =
        List.of(
            deal("Paris", "299.99", "2025-07-01", "2025-07-10", "AirFrance"),
            deal("Tokyo", "599.00", "2025-08-05", "2025-08-15", "ANA"));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-789").build());

    notificationService.sendDealNotification(deals);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String body = captor.getValue().message().body().text().data();

    // First deal fields
    assertTrue(body.contains("Paris"), "Body should contain first deal destination");
    assertTrue(body.contains("299.99"), "Body should contain first deal price");
    assertTrue(body.contains("2025-07-01"), "Body should contain first deal departure");
    assertTrue(body.contains("2025-07-10"), "Body should contain first deal return");
    assertTrue(body.contains("AirFrance"), "Body should contain first deal airline");

    // Second deal fields
    assertTrue(body.contains("Tokyo"), "Body should contain second deal destination");
    assertTrue(body.contains("599.00"), "Body should contain second deal price");
    assertTrue(body.contains("2025-08-05"), "Body should contain second deal departure");
    assertTrue(body.contains("2025-08-15"), "Body should contain second deal return");
    assertTrue(body.contains("ANA"), "Body should contain second deal airline");

    // Both deals numbered
    assertTrue(body.contains("Deal 1:"), "Body should contain Deal 1 label");
    assertTrue(body.contains("Deal 2:"), "Body should contain Deal 2 label");
  }

  // ---- SES throws exception wrapped as RuntimeException ----

  @Test
  void sendDealNotification_sesThrowsException_wrappedAsRuntimeException() {
    List<FlightDeal> deals =
        List.of(deal("Berlin", "199.99", "2025-09-01", "2025-09-10", "Lufthansa"));

    SesException sesException =
        (SesException) SesException.builder().message("Service unavailable").build();
    when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(sesException);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> notificationService.sendDealNotification(deals));

    assertTrue(thrown.getMessage().contains("Failed to send notification email"));
    assertSame(sesException, thrown.getCause());
  }

  // ---- Subject line singular deal count ----

  @Test
  void sendDealNotification_singleDeal_subjectLineSingular() {
    List<FlightDeal> deals =
        List.of(deal("Rome", "350.00", "2025-10-01", "2025-10-08", "Alitalia"));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-single").build());

    notificationService.sendDealNotification(deals);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String subject = captor.getValue().message().subject().data();
    assertEquals("Flight Deal Alert: 1 matching deal found!", subject);
  }

  // ---- Subject line plural deal count ----

  @Test
  void sendDealNotification_multipleDeals_subjectLinePlural() {
    List<FlightDeal> deals =
        List.of(
            deal("Paris", "299.99", "2025-07-01", "2025-07-10", "AirFrance"),
            deal("Tokyo", "599.00", "2025-08-05", "2025-08-15", "ANA"),
            deal("London", "450.00", "2025-09-01", "2025-09-07", "BA"));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-plural").build());

    notificationService.sendDealNotification(deals);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    String subject = captor.getValue().message().subject().data();
    assertEquals("Flight Deal Alert: 3 matching deals found!", subject);
  }

  // ---- formatEmailBody directly testable ----

  @Test
  void formatEmailBody_containsAllFieldsForEachDeal() {
    List<FlightDeal> deals =
        List.of(deal("Sydney", "899.00", "2025-12-20", "2025-12-30", "Qantas"));

    String body = notificationService.formatEmailBody(deals);

    assertTrue(body.contains("Destination: Sydney"));
    assertTrue(body.contains("$899.00"));
    assertTrue(body.contains("Departure: 2025-12-20"));
    assertTrue(body.contains("Return: 2025-12-30"));
    assertTrue(body.contains("Airline: Qantas"));
    assertTrue(body.contains("Happy travels!"));
  }

  // ---- Email request uses correct sender and recipient ----

  @Test
  void sendDealNotification_usesCorrectSenderAndRecipient() {
    List<FlightDeal> deals =
        List.of(deal("Madrid", "275.00", "2025-06-15", "2025-06-22", "Iberia"));

    when(sesClient.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("msg-addr").build());

    notificationService.sendDealNotification(deals);

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(sesClient).sendEmail(captor.capture());

    SendEmailRequest request = captor.getValue();
    assertEquals(SENDER, request.source());
    assertTrue(request.destination().toAddresses().contains(RECIPIENT));
  }
}
