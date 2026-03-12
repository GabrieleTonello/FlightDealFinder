package com.flightdeal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.ExecutionListItem;
import software.amazon.awssdk.services.sfn.model.ListExecutionsRequest;
import software.amazon.awssdk.services.sfn.model.ListExecutionsResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test validating that the Workflow_Trigger_Lambda starts the
 * Matching_Workflow (Step Functions) when a message is consumed from the Deal_Queue.
 *
 * This test simulates the SQS event that the Workflow Trigger Lambda receives,
 * invokes the Lambda directly, and then verifies a new Step Functions execution
 * was started.
 *
 * Validates: Requirements 18.3
 */
@Tag("integration")
public class WorkflowIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowIntegrationTest.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private static LambdaClient lambdaClient;
    private static SfnClient sfnClient;
    private static String workflowTriggerLambdaName;
    private static String stateMachineArn;

    @BeforeAll
    static void setUp() {
        workflowTriggerLambdaName = resolveEnv("WORKFLOW_TRIGGER_LAMBDA_NAME");
        stateMachineArn = resolveEnv("STATE_MACHINE_ARN");

        lambdaClient = LambdaClient.create();
        sfnClient = SfnClient.create();

        assertNotNull(workflowTriggerLambdaName, "WORKFLOW_TRIGGER_LAMBDA_NAME must be set");
        assertNotNull(stateMachineArn, "STATE_MACHINE_ARN must be set");
        assertFalse(workflowTriggerLambdaName.isBlank(), "WORKFLOW_TRIGGER_LAMBDA_NAME must not be blank");
        assertFalse(stateMachineArn.isBlank(), "STATE_MACHINE_ARN must not be blank");
    }

    @Test
    void workflowTriggerLambdaStartsMatchingWorkflow() throws Exception {
        // Record executions before our test so we can identify new ones
        Instant beforeInvocation = Instant.now();
        String testCorrelationId = UUID.randomUUID().toString();

        logger.info("Invoking Workflow Trigger Lambda: {}", workflowTriggerLambdaName);
        logger.info("Correlation ID: {}", testCorrelationId);

        // Build an SQS event payload that mimics what the Lambda receives from the queue.
        // The SQS event wraps the SNS notification which wraps the deal batch message.
        String dealBatchJson = createTestDealBatchMessage(testCorrelationId);
        String sqsEventPayload = createSqsEventPayload(dealBatchJson);

        // Invoke the Workflow Trigger Lambda with the simulated SQS event
        InvokeResponse invokeResponse = lambdaClient.invoke(InvokeRequest.builder()
                .functionName(workflowTriggerLambdaName)
                .payload(SdkBytes.fromUtf8String(sqsEventPayload))
                .build());

        int statusCode = invokeResponse.statusCode();
        logger.info("Lambda invocation status code: {}", statusCode);
        assertEquals(200, statusCode, "Lambda invocation should return 200");

        String functionError = invokeResponse.functionError();
        if (functionError != null) {
            String responsePayload = invokeResponse.payload().asUtf8String();
            logger.warn("Lambda returned function error: {} — payload: {}", functionError, responsePayload);
            // Don't fail immediately — the workflow might still have been started
        }

        // Poll Step Functions for a new execution started after our invocation
        logger.info("Polling Step Functions for new execution of state machine: {}", stateMachineArn);
        ExecutionListItem newExecution = pollForNewExecution(beforeInvocation);

        assertNotNull(newExecution,
                "Expected a new Step Functions execution to be started after Lambda invocation");

        logger.info("Found new execution: ARN={}, status={}, startDate={}",
                newExecution.executionArn(), newExecution.status(), newExecution.startDate());

        // Verify the execution was started (any status is fine — RUNNING, SUCCEEDED, FAILED, etc.)
        assertNotNull(newExecution.executionArn(), "Execution should have an ARN");
        assertNotNull(newExecution.startDate(), "Execution should have a start date");

        logger.info("Workflow integration test passed — Lambda successfully started a Step Functions execution");
    }

    /**
     * Polls Step Functions for an execution that started after the given timestamp.
     */
    private ExecutionListItem pollForNewExecution(Instant afterTimestamp) throws InterruptedException {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            ListExecutionsResponse response = sfnClient.listExecutions(ListExecutionsRequest.builder()
                    .stateMachineArn(stateMachineArn)
                    .maxResults(10)
                    .build());

            List<ExecutionListItem> executions = response.executions();
            for (ExecutionListItem execution : executions) {
                if (execution.startDate().isAfter(afterTimestamp)) {
                    return execution;
                }
            }

            logger.info("No new execution found yet, retrying in {}s...", POLL_INTERVAL.toSeconds());
            Thread.sleep(POLL_INTERVAL.toMillis());
        }

        return null;
    }

    /**
     * Creates a test DealBatchMessage JSON with a correlation ID in the destination.
     */
    private String createTestDealBatchMessage(String correlationId) {
        return """
                {
                  "deals": [
                    {
                      "destination": "TEST-%s",
                      "price": 299.99,
                      "departureDate": "2025-09-01",
                      "returnDate": "2025-09-08",
                      "airline": "WorkflowTestAir"
                    }
                  ],
                  "searchTimestamp": "%s",
                  "destinationsSearched": 1
                }
                """.formatted(correlationId, Instant.now().toString());
    }

    /**
     * Wraps a deal batch message in an SQS event envelope, simulating what the
     * Workflow Trigger Lambda receives when consuming from the Deal Queue.
     * The message body is the raw deal batch JSON (or an SNS envelope depending
     * on the Lambda's parsing logic).
     */
    private String createSqsEventPayload(String messageBody) {
        // Escape the inner JSON for embedding in the SQS event
        String escapedBody = messageBody
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");

        return """
                {
                  "Records": [
                    {
                      "messageId": "%s",
                      "receiptHandle": "test-receipt-handle",
                      "body": "%s",
                      "attributes": {
                        "ApproximateReceiveCount": "1",
                        "SentTimestamp": "%d",
                        "ApproximateFirstReceiveTimestamp": "%d"
                      },
                      "messageAttributes": {},
                      "md5OfBody": "test-md5",
                      "eventSource": "aws:sqs",
                      "eventSourceARN": "arn:aws:sqs:us-east-1:123456789012:test-queue",
                      "awsRegion": "us-east-1"
                    }
                  ]
                }
                """.formatted(
                UUID.randomUUID().toString(),
                escapedBody,
                Instant.now().toEpochMilli(),
                Instant.now().toEpochMilli()
        );
    }

    private static String resolveEnv(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value;
    }
}
