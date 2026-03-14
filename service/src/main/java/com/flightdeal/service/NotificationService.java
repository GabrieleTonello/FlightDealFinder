package com.flightdeal.service;

import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
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
 * <p>Formats an email body containing price, airline, airports, times, and duration for each
 * matched deal, then sends it to the configured recipient. Throws RuntimeException on failure so
 * that Step Functions retry policies can handle transient errors.
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
   * @param matchedFlights the list of matched flight deals to include in the email
   * @return the SES message ID
   * @throws RuntimeException if the email fails to send
   */
  public String sendDealNotification(List<FlightDeal> matchedFlights) {
    log.info(
        "Sending deal notification with {} matched flights to {}",
        matchedFlights.size(),
        recipientEmail);

    String subject =
        String.format(
            "Flight Deal Alert: %d matching deal%s found!",
            matchedFlights.size(), matchedFlights.size() == 1 ? "" : "s");
    String emailBody = formatEmailBody(matchedFlights);

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

  String formatEmailBody(List<FlightDeal> flights) {
    var sb = new StringBuilder();
    sb.append("Great news! We found flight deals that match your calendar availability.\n\n");

    for (int i = 0; i < flights.size(); i++) {
      FlightDeal deal = flights.get(i);
      int price = deal.getPrice();
      int totalDuration = deal.getTotalDuration();

      String airline = "";
      String depAirport = "";
      String depTime = "";
      String arrAirport = "";
      String arrTime = "";

      List<FlightSegment> segments = deal.getFlights();
      if (segments != null && !segments.isEmpty()) {
        FlightSegment firstSeg = segments.get(0);
        FlightSegment lastSeg = segments.get(segments.size() - 1);
        airline = firstSeg.getAirline() != null ? firstSeg.getAirline() : "";

        Airport dep = firstSeg.getDepartureAirport();
        if (dep != null) {
          String depName = dep.getName() != null ? dep.getName() : "";
          String depId = dep.getId() != null ? dep.getId() : "";
          depAirport = depName + " (" + depId + ")";
          depTime = dep.getTime() != null ? dep.getTime() : "";
        }

        Airport arr = lastSeg.getArrivalAirport();
        if (arr != null) {
          String arrName = arr.getName() != null ? arr.getName() : "";
          String arrId = arr.getId() != null ? arr.getId() : "";
          arrAirport = arrName + " (" + arrId + ")";
          arrTime = arr.getTime() != null ? arr.getTime() : "";
        }
      }

      sb.append(String.format("Deal %d:\n", i + 1));
      sb.append(String.format("  Price: %d EUR\n", price));
      sb.append(String.format("  Airline: %s\n", airline));
      sb.append(String.format("  From: %s at %s\n", depAirport, depTime));
      sb.append(String.format("  To: %s at %s\n", arrAirport, arrTime));
      sb.append(String.format("  Duration: %d min\n", totalDuration));
      sb.append("\n");
    }

    sb.append("Happy travels!");
    return sb.toString();
  }
}
