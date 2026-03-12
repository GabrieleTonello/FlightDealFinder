package com.flightdeal.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.SearchError;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.guice.FlightSearchModule;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for the Flight Search function.
 * Triggered by EventBridge on an hourly schedule.
 *
 * Iterates configured destinations, queries the external flight API per destination,
 * writes deals to DynamoDB via PriceRecordDao, publishes a deal batch to SNS,
 * and emits CloudWatch metrics.
 *
 * Per-destination error isolation ensures one failing destination does not block others.
 */
@Slf4j
public class FlightSearchHandler implements RequestHandler<Object, Map<String, Object>> {

    private static final int SNS_MAX_RETRIES = 3;
    private static final long SNS_BASE_DELAY_MS = 100;

    @Inject private FlightApiClient flightApiClient;
    @Inject private PriceRecordDao priceRecordDao;
    @Inject private SnsClient snsClient;
    @Inject private MetricsEmitter metricsEmitter;
    private ObjectMapper objectMapper;
    @Inject @Named("topicArn") private String topicArn;
    @Inject @Named("destinations") private List<String> destinations;

    /**
     * No-arg constructor for Lambda runtime.
     * Creates a Guice injector with FlightSearchModule and injects dependencies.
     */
    public FlightSearchHandler() {
        Guice.createInjector(new FlightSearchModule()).injectMembers(this);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor for dependency injection (used by Guice and tests).
     */
    @Inject
    public FlightSearchHandler(FlightApiClient flightApiClient,
                               PriceRecordDao priceRecordDao,
                               SnsClient snsClient,
                               MetricsEmitter metricsEmitter,
                               @Named("topicArn") String topicArn,
                               @Named("destinations") List<String> destinations) {
        this.flightApiClient = flightApiClient;
        this.priceRecordDao = priceRecordDao;
        this.snsClient = snsClient;
        this.metricsEmitter = metricsEmitter;
        this.objectMapper = new ObjectMapper();
        this.topicArn = topicArn;
        this.destinations = destinations;
    }

    @Override
    public Map<String, Object> handleRequest(Object event, Context context) {
        long startTime = System.currentTimeMillis();
        List<FlightDeal> allDeals = new ArrayList<>();
        List<SearchError> errors = new ArrayList<>();

        // Iterate all configured destinations with per-destination error isolation (Req 2.1, 2.3)
        for (String destination : destinations) {
            try {
                List<FlightDeal> deals = flightApiClient.searchDeals(destination.trim());

                // Skip destinations with empty results (Req 2.4)
                if (deals == null || deals.isEmpty()) {
                    log.info("No deals found for destination: {}", destination);
                    continue;
                }

                allDeals.addAll(deals);
            } catch (FlightApiException e) {
                // Per-destination error isolation: log and continue (Req 2.3)
                log.warn("Flight API error for destination {}: {} [{}]",
                        e.getDestination(), e.getMessage(), e.getErrorType(), e);
                errors.add(SearchError.builder()
                        .destination(e.getDestination())
                        .errorMessage(e.getMessage())
                        .errorType(e.getErrorType())
                        .build());
            }
        }

        // Write deals to DynamoDB Price Store via DAO (Req 3.1, 3.2, 3.3)
        String retrievalTimestamp = Instant.now().toString();
        if (!allDeals.isEmpty()) {
            List<PriceRecordEntity> entities = allDeals.stream()
                    .map(deal -> PriceRecordEntity.builder()
                            .destination(deal.getDestination())
                            .timestamp(retrievalTimestamp)
                            .price(deal.getPrice())
                            .departureDate(deal.getDepartureDate())
                            .returnDate(deal.getReturnDate())
                            .airline(deal.getAirline())
                            .retrievalTimestamp(retrievalTimestamp)
                            .build())
                    .toList();
            priceRecordDao.saveBatch(entities);
        }

        // Publish deal batch to SNS (Req 4.1, 4.2, 4.3)
        if (!allDeals.isEmpty()) {
            publishDealBatch(allDeals, retrievalTimestamp);
        }

        // Emit CloudWatch metrics (Req 11.1)
        long duration = System.currentTimeMillis() - startTime;
        metricsEmitter.emitDealsFound(allDeals.size());
        metricsEmitter.emitDestinationsSearched(destinations.size());
        metricsEmitter.emitExecutionDuration(duration);

        // Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dealsFound", allDeals.size());
        result.put("destinationsSearched", destinations.size());
        result.put("errorsCount", errors.size());
        result.put("durationMs", duration);
        return result;
    }

    /**
     * Publishes a deal batch message to SNS with retry (up to 3 times, exponential backoff).
     * Message contains: deals list, searchTimestamp, destinationsSearched count (Req 4.1, 4.2, 4.3).
     */
    void publishDealBatch(List<FlightDeal> deals, String searchTimestamp) {
        Map<String, Object> batchMessage = new LinkedHashMap<>();

        List<Map<String, Object>> dealMaps = deals.stream()
                .map(deal -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("destination", deal.getDestination());
                    m.put("price", deal.getPrice());
                    m.put("departureDate", deal.getDepartureDate());
                    m.put("returnDate", deal.getReturnDate());
                    m.put("airline", deal.getAirline());
                    return m;
                })
                .toList();

        batchMessage.put("deals", dealMaps);
        batchMessage.put("searchTimestamp", searchTimestamp);
        batchMessage.put("destinationsSearched", destinations.size());

        String messageBody;
        try {
            messageBody = objectMapper.writeValueAsString(batchMessage);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize deal batch message", e);
            return;
        }

        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(messageBody)
                .build();

        publishToSnsWithRetry(request);
    }

    /**
     * Publishes to SNS with retry up to SNS_MAX_RETRIES times with exponential backoff.
     * Base delay doubles on each retry: 100ms, 200ms, 400ms.
     */
    void publishToSnsWithRetry(PublishRequest request) {
        int attempt = 0;
        while (true) {
            try {
                snsClient.publish(request);
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= SNS_MAX_RETRIES) {
                    log.error("SNS publish failed after {} attempts: {}", SNS_MAX_RETRIES, e.getMessage(), e);
                    return;
                }
                long delay = SNS_BASE_DELAY_MS * (1L << (attempt - 1));
                log.warn("SNS publish attempt {} failed, retrying in {}ms: {}", attempt, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("SNS publish retry interrupted", ie);
                    return;
                }
            }
        }
    }
}
