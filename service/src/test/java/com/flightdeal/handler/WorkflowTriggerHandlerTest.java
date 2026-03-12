package com.flightdeal.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightdeal.metrics.MetricsEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SfnException;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowTriggerHandlerTest {

    @Mock private SfnClient sfnClient;
    @Mock private MetricsEmitter metricsEmitter;
    @Mock private Context context;

    private static final String STATE_MACHINE_ARN = "arn:aws:states:us-east-1:123456789:stateMachine:MatchingWorkflow";

    private WorkflowTriggerHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new WorkflowTriggerHandler(sfnClient, metricsEmitter, objectMapper, STATE_MACHINE_ARN);
    }

    private SQSEvent createSqsEvent(List<String> bodies) {
        SQSEvent event = new SQSEvent();
        List<SQSEvent.SQSMessage> records = bodies.stream().map(body -> {
            SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
            message.setBody(body);
            message.setMessageId("msg-" + body.hashCode());
            return message;
        }).toList();
        event.setRecords(records);
        return event;
    }

    private String dealBatchJson() {
        return "{\"deals\":[{\"destination\":\"Paris\",\"price\":299.99,\"departureDate\":\"2025-03-01\",\"returnDate\":\"2025-03-08\",\"airline\":\"AirFrance\"}],\"searchTimestamp\":\"2025-01-15T10:00:00Z\",\"destinationsSearched\":1}";
    }

    // ---- Successful workflow start ----

    @Test
    void handleRequest_successfulStart_callsStartExecutionWithCorrectArnAndInput() {
        String body = dealBatchJson();
        SQSEvent event = createSqsEvent(List.of(body));

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenReturn(StartExecutionResponse.builder()
                        .executionArn("arn:aws:states:us-east-1:123456789:execution:MatchingWorkflow:deal-workflow-123")
                        .build());

        handler.handleRequest(event, context);

        ArgumentCaptor<StartExecutionRequest> captor = ArgumentCaptor.forClass(StartExecutionRequest.class);
        verify(sfnClient).startExecution(captor.capture());

        StartExecutionRequest request = captor.getValue();
        assertEquals(STATE_MACHINE_ARN, request.stateMachineArn());
        assertEquals(body, request.input());
        assertTrue(request.name().startsWith("deal-workflow-"));
    }

    @Test
    void handleRequest_successfulStart_emitsWorkflowsStartedMetric() {
        SQSEvent event = createSqsEvent(List.of(dealBatchJson()));

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenReturn(StartExecutionResponse.builder()
                        .executionArn("arn:exec")
                        .build());

        handler.handleRequest(event, context);

        verify(metricsEmitter).emitWorkflowsStarted(1);
        verify(metricsEmitter, never()).emitStartFailures(anyInt());
    }

    // ---- StartExecution failure ----

    @Test
    void handleRequest_startExecutionFails_throwsRuntimeException() {
        SQSEvent event = createSqsEvent(List.of(dealBatchJson()));

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenThrow(SfnException.builder().message("Access denied").build());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> handler.handleRequest(event, context));

        assertTrue(ex.getMessage().contains("Failed to process deal batch message"));
    }

    @Test
    void handleRequest_startExecutionFails_emitsStartFailuresMetric() {
        SQSEvent event = createSqsEvent(List.of(dealBatchJson()));

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenThrow(SfnException.builder().message("Throttled").build());

        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, context));

        verify(metricsEmitter).emitStartFailures(1);
        verify(metricsEmitter, never()).emitWorkflowsStarted(anyInt());
    }

    // ---- Multiple SQS records ----

    @Test
    void handleRequest_multipleSqsRecords_startsWorkflowForEach() {
        String body1 = "{\"deals\":[],\"searchTimestamp\":\"2025-01-15T10:00:00Z\",\"destinationsSearched\":0}";
        String body2 = dealBatchJson();
        SQSEvent event = createSqsEvent(List.of(body1, body2));

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenReturn(StartExecutionResponse.builder().executionArn("arn:exec1").build())
                .thenReturn(StartExecutionResponse.builder().executionArn("arn:exec2").build());

        handler.handleRequest(event, context);

        ArgumentCaptor<StartExecutionRequest> captor = ArgumentCaptor.forClass(StartExecutionRequest.class);
        verify(sfnClient, times(2)).startExecution(captor.capture());

        List<StartExecutionRequest> requests = captor.getAllValues();
        assertEquals(body1, requests.get(0).input());
        assertEquals(body2, requests.get(1).input());
        assertEquals(STATE_MACHINE_ARN, requests.get(0).stateMachineArn());
        assertEquals(STATE_MACHINE_ARN, requests.get(1).stateMachineArn());

        verify(metricsEmitter, times(2)).emitWorkflowsStarted(1);
    }

    @Test
    void handleRequest_secondRecordFails_throwsAfterFirstSucceeds() {
        String body1 = "{\"deals\":[],\"searchTimestamp\":\"2025-01-15T10:00:00Z\",\"destinationsSearched\":0}";
        String body2 = dealBatchJson();
        SQSEvent event = createSqsEvent(List.of(body1, body2));

        when(sfnClient.startExecution(any(StartExecutionRequest.class)))
                .thenReturn(StartExecutionResponse.builder().executionArn("arn:exec1").build())
                .thenThrow(SfnException.builder().message("Limit exceeded").build());

        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, context));

        verify(metricsEmitter, times(1)).emitWorkflowsStarted(1);
        verify(metricsEmitter, times(1)).emitStartFailures(1);
    }
}
