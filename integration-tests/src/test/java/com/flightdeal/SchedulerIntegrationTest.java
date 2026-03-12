package com.flightdeal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.core.SdkBytes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test validating that the Scheduler triggers the Flight_Search_Lambda
 * and deal records appear in the Price_Store (DynamoDB).
 *
 * Validates: Requirements 18.1
 */
@Tag("integration")
public class SchedulerIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerIntegrationTest.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private static LambdaClient lambdaClient;
    private static DynamoDbClient dynamoDbClient;
    private static String flightSearchLambdaName;
    private static String tableName;

    @BeforeAll
    static void setUp() {
        flightSearchLambdaName = resolveEnv("FLIGHT_SEARCH_LAMBDA_NAME");
        tableName = resolveEnv("TABLE_NAME");

        lambdaClient = LambdaClient.create();
        dynamoDbClient = DynamoDbClient.create();

        assertNotNull(flightSearchLambdaName, "FLIGHT_SEARCH_LAMBDA_NAME must be set");
        assertNotNull(tableName, "TABLE_NAME must be set");
        assertFalse(flightSearchLambdaName.isBlank(), "FLIGHT_SEARCH_LAMBDA_NAME must not be blank");
        assertFalse(tableName.isBlank(), "TABLE_NAME must not be blank");
    }

    @Test
    void schedulerTriggersLambdaAndDealsAppearInPriceStore() throws Exception {
        // Record the timestamp before invocation to scope our DynamoDB query
        String timestampBefore = Instant.now().toString();
        logger.info("Invoking Flight Search Lambda: {}", flightSearchLambdaName);

        // Simulate the EventBridge scheduled event by invoking the Lambda directly
        // EventBridge sends a JSON payload; we use a minimal scheduled event shape
        String scheduledEventPayload = """
                {
                  "source": "aws.events",
                  "detail-type": "Scheduled Event",
                  "detail": {}
                }
                """;

        InvokeResponse invokeResponse = lambdaClient.invoke(InvokeRequest.builder()
                .functionName(flightSearchLambdaName)
                .payload(SdkBytes.fromUtf8String(scheduledEventPayload))
                .build());

        int statusCode = invokeResponse.statusCode();
        logger.info("Lambda invocation status code: {}", statusCode);
        assertEquals(200, statusCode, "Lambda invocation should return 200");

        // Check for function error (unhandled exception in the Lambda)
        String functionError = invokeResponse.functionError();
        if (functionError != null) {
            String responsePayload = invokeResponse.payload().asUtf8String();
            logger.warn("Lambda returned function error: {} — payload: {}", functionError, responsePayload);
        }

        // Poll DynamoDB for records created after our timestamp
        logger.info("Polling DynamoDB table '{}' for new deal records...", tableName);
        List<Map<String, AttributeValue>> newRecords = pollForRecords(timestampBefore);

        assertFalse(newRecords.isEmpty(),
                "Expected at least one deal record in the Price_Store after Lambda invocation");

        // Validate that records contain the required fields
        for (Map<String, AttributeValue> record : newRecords) {
            logger.info("Found record: destination={}, timestamp={}",
                    record.get("destination"), record.get("timestamp"));

            assertNotNull(record.get("destination"), "Record must have 'destination' partition key");
            assertNotNull(record.get("timestamp"), "Record must have 'timestamp' sort key");
            assertNotNull(record.get("price"), "Record must have 'price'");
            assertNotNull(record.get("departureDate"), "Record must have 'departureDate'");
            assertNotNull(record.get("returnDate"), "Record must have 'returnDate'");
            assertNotNull(record.get("airline"), "Record must have 'airline'");
        }

        // Cleanup: delete the records we just verified
        cleanupRecords(newRecords);
        logger.info("Scheduler integration test passed — {} records verified and cleaned up", newRecords.size());
    }

    /**
     * Polls DynamoDB using a scan with a timestamp filter to find records
     * created after the given timestamp. Uses a simple scan since we don't
     * know which destinations will have deals.
     */
    private List<Map<String, AttributeValue>> pollForRecords(String timestampBefore) throws InterruptedException {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            var scanResponse = dynamoDbClient.scan(builder -> builder
                    .tableName(tableName)
                    .filterExpression("#ts > :tsVal")
                    .expressionAttributeNames(Map.of("#ts", "timestamp"))
                    .expressionAttributeValues(Map.of(
                            ":tsVal", AttributeValue.builder().s(timestampBefore).build()
                    ))
            );

            if (!scanResponse.items().isEmpty()) {
                return scanResponse.items();
            }

            logger.info("No records found yet, retrying in {}s...", POLL_INTERVAL.toSeconds());
            Thread.sleep(POLL_INTERVAL.toMillis());
        }

        return List.of();
    }

    /**
     * Deletes the test records from DynamoDB to leave the table clean.
     */
    private void cleanupRecords(List<Map<String, AttributeValue>> records) {
        for (Map<String, AttributeValue> record : records) {
            try {
                dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "destination", record.get("destination"),
                                "timestamp", record.get("timestamp")
                        ))
                        .build());
            } catch (Exception e) {
                logger.warn("Failed to clean up record: {}", e.getMessage());
            }
        }
    }

    private static String resolveEnv(String name) {
        // Check system property first (passed via Gradle), then environment variable
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value;
    }
}
