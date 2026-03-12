package com.flightdeal.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.SearchError;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lambda handler for the Flight Search function.
 * Triggered by EventBridge on an hourly schedule.
 *
 * Iterates configured destinations, queries the external flight API per destination,
 * writes deals to DynamoDB, publishes a deal batch to SNS, and emits CloudWatch metrics.
 *
 * Per-destination error isolation ensures one failing destination does not block others.
 */
public class FlightSearchHandler implements RequestHandler<Object, Map<String, Object>> {

    private static final Logger LOG = Logger.getLogger(FlightSearchHandler.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 100;

    private final FlightApiClient flightApiClient;
    private final DynamoDbClient dynamoDbClient;
    private final SnsClient snsClient;
    private final MetricsEmitter metricsEmitter;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final String topicArn;
    private final List<String> destinations;

    /**
     * Constructor for dependency injection (used in tests).
     */
    public FlightSearchHandler(FlightApiClient flightApiClient,
                               DynamoDbClient dynamoDbClient,
                               SnsClient snsClient,
                               MetricsEmitter metricsEmitter,
                               String tableName,
                               String topicArn,
                               List<String> destinations) {
        this.flightApiClient = flightApiClient;
        this.dynamoDbClient = dynamoDbClient;
        this.snsClient = snsClient;
        this.metricsEmitter = metricsEmitter;
        this.objectMapper = new ObjectMapper();
        this.tableName = tableName;
        this.topicArn = topicArn;
        this.destinations = destinations;
    }

    /**
     * No-arg constructor for Lambda runtime.
     * Creates real AWS clients and reads configuration from environment variables.
     */
    public FlightSearchHandler() {
        String apiBaseUrl = System.getenv("FLIGHT_API_BASE_URL");
        String apiKey = System.getenv("FLIGHT_API_KEY");
        this.flightApiClient = new FlightApiClient(
                HttpClient.newHttpClient(), apiBaseUrl, apiKey, Duration.ofSeconds(30));
        this.dynamoDbClient = DynamoDbClient.create();
        this.snsClient = SnsClient.create();
        this.metricsEmitter = new MetricsEmitter(
                software.amazon.awssdk.services.cloudwatch.CloudWatchClient.create());
        this.objectMapper = new ObjectMapper();
        this.tableName = System.getenv("TABLE_NAME");
        this.topicArn = System.getenv("TOPIC_ARN");
        String destinationsEnv = System.getenv("DESTINATIONS");
        this.destinations = (destinationsEnv != null && !destinationsEnv.isBlank())
                ? List.of(destinationsEnv.split(","))
                : List.of();
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
                    LOG.info("No deals found for destination: " + destination);
                    continue;
                }

                allDeals.addAll(deals);
            } catch (FlightApiException e) {
                // Per-destination error isolation: log and continue (Req 2.3)
                LOG.log(Level.WARNING, "Flight API error for destination " + e.getDestination()
                        + ": " + e.getMessage() + " [" + e.getErrorType() + "]", e);
                errors.add(SearchError.builder()
                        .destination(e.getDestination())
                        .errorMessage(e.getMessage())
                        .errorType(e.getErrorType())
                        .build());
            }
        }

        // Write deals to DynamoDB Price Store (Req 3.1, 3.2, 3.3)
        String retrievalTimestamp = Instant.now().toString();
        for (FlightDeal deal : allDeals) {
            writeDealToDynamoDb(deal, retrievalTimestamp);
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
     * Writes a single deal record to DynamoDB with retry (up to 3 times, exponential backoff).
     * Partition key: destination, Sort key: ISO-8601 timestamp (Req 3.1, 3.2, 3.3).
     */
    void writeDealToDynamoDb(FlightDeal deal, String retrievalTimestamp) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("destination", AttributeValue.fromS(deal.getDestination()));
        item.put("timestamp", AttributeValue.fromS(retrievalTimestamp));
        item.put("price", AttributeValue.fromN(deal.getPrice().toPlainString()));
        item.put("departureDate", AttributeValue.fromS(deal.getDepartureDate()));
        item.put("returnDate", AttributeValue.fromS(deal.getReturnDate()));
        item.put("airline", AttributeValue.fromS(deal.getAirline()));
        item.put("retrievalTimestamp", AttributeValue.fromS(retrievalTimestamp));

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        executeWithRetry(() -> dynamoDbClient.putItem(request),
                "DynamoDB write for " + deal.getDestination());
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
            LOG.log(Level.SEVERE, "Failed to serialize deal batch message", e);
            return;
        }

        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(messageBody)
                .build();

        executeWithRetry(() -> {
            snsClient.publish(request);
            return null;
        }, "SNS publish");
    }

    /**
     * Executes an operation with retry up to MAX_RETRIES times with exponential backoff.
     * Base delay doubles on each retry: 100ms, 200ms, 400ms.
     */
    void executeWithRetry(RetryableOperation operation, String operationName) {
        int attempt = 0;
        while (true) {
            try {
                operation.execute();
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    LOG.log(Level.SEVERE, operationName + " failed after " + MAX_RETRIES
                            + " attempts: " + e.getMessage(), e);
                    return;
                }
                long delay = BASE_DELAY_MS * (1L << (attempt - 1));
                LOG.log(Level.WARNING, operationName + " attempt " + attempt
                        + " failed, retrying in " + delay + "ms: " + e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.log(Level.SEVERE, operationName + " retry interrupted", ie);
                    return;
                }
            }
        }
    }

    /**
     * Functional interface for retryable operations.
     */
    @FunctionalInterface
    interface RetryableOperation {
        void execute() throws Exception;
    }
}
