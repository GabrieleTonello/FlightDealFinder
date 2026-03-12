package com.flightdeal.service;

import com.flightdeal.generated.model.FlightDeal;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

/**
 * Sends email notifications for matched flight deals via Amazon SES.
 *
 * <p>Formats an email body containing destination, price, departure date, return date, and airline
 * for each matched deal, then sends it to the configured recipient. Throws RuntimeException on
 * failure so that Step Functions retry policies can handle transient errors.
 */
@Slf4j
@Singleton
public class NotificationService {

  private final SesClient sesClient;
  private final String senderEmail;
  private final String recipientEmail;

  @Inject
  public NotificationService(
      SesClient sesClient,
      @Named("SENDER_EMAIL") String senderEmail,
      @Named("RECIPIENT_EMAIL") String recipientEmail) {
    this.sesClient = sesClient;
    this.senderEmail = senderEmail;
    this.recipientEmail = recipientEmail;
  }

  /**
   * Sends an email notification with the matched flight deals.
   *
   * @param matchedDeals the list of matched flight deals to include in the email
   * @return the SES message ID
   * @throws RuntimeException if the email fails to send
   */
  public String sendDealNotification(List<FlightDeal> matchedDeals) {
    log.info(
        "Sending deal notification with {} matched deals to {}",
        matchedDeals.size(),
        recipientEmail);

    String subject =
        String.format(
            "Flight Deal Alert: %d matching deal%s found!",
            matchedDeals.size(), matchedDeals.size() == 1 ? "" : "s");
    String emailBody = formatEmailBody(matchedDeals);

    try {
      SendEmailRequest request =
          SendEmailRequest.builder()
              .source(senderEmail)
              .destination(Destination.builder().toAddresses(recipientEmail).build())
              .message(
                  Message.builder()
                      .subject(Content.builder().data(subject).charset("UTF-8").build())
                      .body(
                          Body.builder()
                              .text(Content.builder().data(emailBody).charset("UTF-8").build())
                              .build())
                      .build())
              .build();

      SendEmailResponse response = sesClient.sendEmail(request);
      String messageId = response.messageId();
      log.info("Email sent successfully: messageId={}", messageId);
      return messageId;
    } catch (Exception e) {
      log.error("Failed to send deal notification email to {}", recipientEmail, e);
      throw new RuntimeException("Failed to send notification email: " + e.getMessage(), e);
    }
  }

  String formatEmailBody(List<FlightDeal> deals) {
    var sb = new StringBuilder();
    sb.append("Great news! We found flight deals that match your calendar availability.\n\n");

    for (int i = 0; i < deals.size(); i++) {
      FlightDeal deal = deals.get(i);
      sb.append(String.format("Deal %d:\n", i + 1));
      sb.append(String.format("  Destination: %s\n", deal.getDestination()));
      sb.append(String.format("  Price: $%s\n", deal.getPrice()));
      sb.append(String.format("  Departure: %s\n", deal.getDepartureDate()));
      sb.append(String.format("  Return: %s\n", deal.getReturnDate()));
      sb.append(String.format("  Airline: %s\n", deal.getAirline()));
      sb.append("\n");
    }

    sb.append("Happy travels!");
    return sb.toString();
  }
}
