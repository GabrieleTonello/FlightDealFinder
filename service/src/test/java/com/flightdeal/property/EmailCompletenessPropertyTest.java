// Feature: flight-deal-notifier, Property 12: Notification email contains all deal fields
package com.flightdeal.property;

import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.service.NotificationService;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 12: For any non-empty list of matched deals, the email body contains
 * destination, price, departureDate, returnDate, airline for every deal.
 *
 * Validates: Requirements 9.1, 9.2
 */
class EmailCompletenessPropertyTest {

    @Property(tries = 100)
    void emailBodyContainsAllDealFields(@ForAll("dealList") List<DealInput> dealInputs) {
        SesClient sesClient = mock(SesClient.class);
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("test-msg-id").build());

        NotificationService service = new NotificationService(
                sesClient, "sender@test.com", "recipient@test.com");

        List<FlightDeal> deals = dealInputs.stream()
                .map(input -> FlightDeal.builder()
                        .destination(input.destination)
                        .price(input.price)
                        .departureDate(input.departureDate)
                        .returnDate(input.returnDate)
                        .airline(input.airline)
                        .build())
                .collect(Collectors.toList());

        service.sendDealNotification(deals);

        // Capture the SES request to inspect the email body
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());

        String emailBody = captor.getValue().message().body().text().data();

        for (FlightDeal deal : deals) {
            assertTrue(emailBody.contains(deal.getDestination()),
                    "Email should contain destination: " + deal.getDestination());
            assertTrue(emailBody.contains(deal.getPrice().toString()),
                    "Email should contain price: " + deal.getPrice());
            assertTrue(emailBody.contains(deal.getDepartureDate()),
                    "Email should contain departureDate: " + deal.getDepartureDate());
            assertTrue(emailBody.contains(deal.getReturnDate()),
                    "Email should contain returnDate: " + deal.getReturnDate());
            assertTrue(emailBody.contains(deal.getAirline()),
                    "Email should contain airline: " + deal.getAirline());
        }
    }

    @Provide
    Arbitrary<List<DealInput>> dealList() {
        Arbitrary<DealInput> dealArb = Arbitraries.of("Paris", "Tokyo", "London", "Berlin", "Sydney", "Rome")
                .flatMap(dest -> Arbitraries.bigDecimals()
                        .between(new BigDecimal("50.00"), new BigDecimal("2000.00"))
                        .ofScale(2)
                        .flatMap(price -> Arbitraries.integers().between(0, 179)
                                .flatMap(depOffset -> Arbitraries.integers().between(3, 21)
                                        .flatMap(tripLen -> Arbitraries.of("AirFrance", "Delta", "ANA", "Lufthansa", "BA", "JAL")
                                                .map(airline -> {
                                                    LocalDate dep = LocalDate.of(2025, 1, 1).plusDays(depOffset);
                                                    LocalDate ret = dep.plusDays(tripLen);
                                                    return new DealInput(dest, price, dep.toString(), ret.toString(), airline);
                                                })))));
        return dealArb.list().ofMinSize(1).ofMaxSize(5);
    }

    record DealInput(String destination, BigDecimal price, String departureDate,
                     String returnDate, String airline) {}
}
