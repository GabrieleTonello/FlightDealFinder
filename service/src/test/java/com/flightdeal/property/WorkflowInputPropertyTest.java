// Feature: flight-deal-notifier, Property 7: Workflow started with correct deal data
package com.flightdeal.property;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightdeal.handler.WorkflowTriggerHandler;
import com.flightdeal.metrics.MetricsEmitter;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 7: For any valid DealBatchMessage JSON, the Step Functions StartExecutionRequest
 * input matches the original message.
 *
 * Validates: Requirements 6.2
 */
class WorkflowInputPropertyTest {

    private static final String STATE_MACHINE_ARN = "arn:aws:states:us-east-1:123456789:stateMachine:TestWorkflow";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Property(tries = 100)
    void workflowInputMatchesDealBatchMessage(
            @ForAll("dealBatchJson") String dealBatchJson) throws Exception {

        SfnClient sfnClient = mock(SfnClient.class);
        MetricsEmitter metricsEmitter = mock(MetricsEmitter.class);

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenReturn(StartExecutionResponse.builder()
                        .executionArn("arn:aws:states:us-east-1:123456789:execution:test")
                        .build());

        WorkflowTriggerHandler handler = new WorkflowTriggerHandler(
                sfnClient, metricsEmitter, objectMapper, STATE_MACHINE_ARN);

        SQSEvent event = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody(dealBatchJson);
        message.setMessageId("test-msg-id");
        event.setRecords(List.of(message));

        handler.handleRequest(event, mock(Context.class));

        ArgumentCaptor<StartExecutionRequest> captor = ArgumentCaptor.forClass(StartExecutionRequest.class);
        verify(sfnClient).startExecution(captor.capture());

        StartExecutionRequest request = captor.getValue();
        assertEquals(STATE_MACHINE_ARN, request.stateMachineArn());
        assertEquals(dealBatchJson, request.input(),
                "Step Functions input should match the original SQS message body");
    }

    @Provide
    Arbitrary<String> dealBatchJson() {
        return Arbitraries.integers().between(1, 5).flatMap(dealCount -> {
            return Arbitraries.of("Paris", "Tokyo", "London", "Berlin", "Sydney")
                    .list().ofSize(dealCount)
                    .map(destinations -> {
                        var deals = destinations.stream().map(dest -> Map.of(
                                "destination", (Object) dest,
                                "price", 100 + (int) (Math.random() * 900),
                                "departureDate", "2025-06-01",
                                "returnDate", "2025-06-08",
                                "airline", "TestAir"
                        )).toList();
                        Map<String, Object> batch = Map.of(
                                "deals", deals,
                                "searchTimestamp", "2025-06-01T00:00:00Z",
                                "destinationsSearched", dealCount
                        );
                        try {
                            return objectMapper.writeValueAsString(batch);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        });
    }
}
