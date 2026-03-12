package com.flightdeal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test validating that messages published to the Deal_Topic (SNS)
 * are received by the Deal_Queue (SQS).
 *
 * Validates: Requirements 18.2
 */
@Tag("integration")
public class MessagingIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(MessagingIntegrationTest.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_POLL_ATTEMPTS = 6;

    private static SnsClient snsClient;
    private static SqsClient sqsClient;
    private static String topicArn;
    private static String queueUrl;

    private final List<String> receiptHandlesToDelete = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        topicArn = resolveEnv("TOPIC_ARN");
        queueUrl = resolveEnv("QUEUE_URL");

        snsClient = SnsClient.create();
        sqsClient = SqsClient.create();

        assertNotNull(topicArn, "TOPIC_ARN must be set");
        assertNotNull(queueUrl, "QUEUE_URL must be set");
        assertFalse(topicArn.isBlank(), "TOPIC_ARN must not be blank");
        assertFalse(queueUrl.isBlank(), "QUEUE_URL must not be blank");
    }

    @AfterEach
    void cleanup() {
        // Delete any messages we received during the test
        for (String receiptHandle : receiptHandlesToDelete) {
            try {
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(receiptHandle)
                        .build());
            } catch (Exception e) {
                logger.warn("Failed to delete SQS message: {}", e.getMessage());
            }
        }
        receiptHandlesToDelete.clear();
    }

    @Test
    void messagePublishedToTopicIsReceivedByQueue() throws Exception {
        // Create a unique test message so we can identify it in the queue
        String testCorrelationId = UUID.randomUUID().toString();
        String testMessage = createTestDealBatchMessage(testCorrelationId);

        logger.info("Publishing test message to SNS topic: {}", topicArn);
        logger.info("Correlation ID: {}", testCorrelationId);

        // Publish to SNS Deal Topic
        PublishResponse publishResponse = snsClient.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(testMessage)
                .build());

        assertNotNull(publishResponse.messageId(), "SNS publish should return a message ID");
        logger.info("Published SNS message ID: {}", publishResponse.messageId());

        // Poll SQS Deal Queue for the message
        logger.info("Polling SQS queue for the test message...");
        Message receivedMessage = pollForMessage(testCorrelationId);

        assertNotNull(receivedMessage, "Expected to receive the test message in the Deal_Queue within timeout");

        // SNS wraps the original message in an envelope — extract and verify
        String body = receivedMessage.body();
        logger.info("Received SQS message body (SNS envelope): {}", body);

        // The SNS envelope contains a "Message" field with our original payload
        ObjectMapper mapper = new ObjectMapper();
        var envelope = mapper.readTree(body);
        assertTrue(envelope.has("Message"), "SQS message body should contain SNS 'Message' field");

        String innerMessage = envelope.get("Message").asText();
        assertTrue(innerMessage.contains(testCorrelationId),
                "Inner message should contain our correlation ID");

        // Verify the inner message is valid JSON with expected structure
        var dealBatch = mapper.readTree(innerMessage);
        assertTrue(dealBatch.has("deals"), "Deal batch message should contain 'deals' field");
        assertTrue(dealBatch.has("searchTimestamp"), "Deal batch message should contain 'searchTimestamp'");
        assertTrue(dealBatch.has("destinationsSearched"), "Deal batch message should contain 'destinationsSearched'");

        logger.info("Messaging integration test passed — message flowed from SNS topic to SQS queue");
    }

    /**
     * Polls the SQS queue looking for a message containing the given correlation ID.
     */
    private Message pollForMessage(String correlationId) throws InterruptedException {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(5)
                    .build());

            for (Message message : response.messages()) {
                receiptHandlesToDelete.add(message.receiptHandle());

                if (message.body().contains(correlationId)) {
                    logger.info("Found matching message on attempt {}", attempt + 1);
                    return message;
                }
            }

            logger.info("Message not found on attempt {}/{}, retrying...", attempt + 1, MAX_POLL_ATTEMPTS);
        }

        return null;
    }

    /**
     * Creates a test DealBatchMessage JSON string with a unique correlation ID
     * embedded in the destination field for identification.
     */
    private String createTestDealBatchMessage(String correlationId) {
        return """
                {
                  "deals": [
                    {
                      "destination": "TEST-%s",
                      "price": 199.99,
                      "departureDate": "2025-08-01",
                      "returnDate": "2025-08-08",
                      "airline": "IntegrationTestAir"
                    }
                  ],
                  "searchTimestamp": "%s",
                  "destinationsSearched": 1
                }
                """.formatted(correlationId, Instant.now().toString());
    }

    private static String resolveEnv(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value;
    }
}
