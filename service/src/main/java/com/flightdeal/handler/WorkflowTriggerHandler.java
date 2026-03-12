package com.flightdeal.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightdeal.guice.WorkflowTriggerModule;
import com.flightdeal.metrics.MetricsEmitter;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import java.util.UUID;

/**
 * Lambda handler for the Workflow Trigger function.
 * Triggered by SQS Deal Queue messages.
 *
 * Parses DealBatchMessage from each SQS record, starts a Step Functions
 * Matching Workflow execution with the deal data as input, and emits
 * CloudWatch metrics for workflows started and start failures.
 *
 * Throws on StartExecution failure so SQS retries the message.
 */
@Slf4j
public class WorkflowTriggerHandler implements RequestHandler<SQSEvent, Void> {

    private final SfnClient sfnClient;
    private final MetricsEmitter metricsEmitter;
    private final ObjectMapper objectMapper;
    private final String stateMachineArn;

    /**
     * No-arg constructor for Lambda runtime.
     * Creates a Guice injector with WorkflowTriggerModule and injects dependencies.
     */
    public WorkflowTriggerHandler() {
        var injector = Guice.createInjector(new WorkflowTriggerModule());
        this.sfnClient = injector.getInstance(SfnClient.class);
        this.metricsEmitter = injector.getInstance(MetricsEmitter.class);
        this.objectMapper = injector.getInstance(ObjectMapper.class);
        this.stateMachineArn = injector.getInstance(
                com.google.inject.Key.get(String.class, com.google.inject.name.Names.named("STATE_MACHINE_ARN")));
    }

    /**
     * Constructor for dependency injection (used by Guice and tests).
     */
    @Inject
    public WorkflowTriggerHandler(SfnClient sfnClient,
                                  MetricsEmitter metricsEmitter,
                                  ObjectMapper objectMapper,
                                  @Named("STATE_MACHINE_ARN") String stateMachineArn) {
        this.sfnClient = sfnClient;
        this.metricsEmitter = metricsEmitter;
        this.objectMapper = objectMapper;
        this.stateMachineArn = stateMachineArn;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            processRecord(record);
        }
        return null;
    }

    /**
     * Processes a single SQS record: parses the deal batch message,
     * starts a Step Functions execution, and emits metrics.
     * Throws RuntimeException on failure so SQS retries the message (Req 6.3).
     */
    private void processRecord(SQSEvent.SQSMessage record) {
        String body = record.getBody();
        log.info("Processing SQS record: messageId={}", record.getMessageId());

        try {
            // Parse deal batch from SQS message body to validate JSON (Req 6.1)
            JsonNode dealBatch = objectMapper.readTree(body);
            int dealCount = dealBatch.has("deals") ? dealBatch.get("deals").size() : 0;
            String searchTimestamp = dealBatch.has("searchTimestamp")
                    ? dealBatch.get("searchTimestamp").asText() : "unknown";
            log.info("Parsed deal batch: deals={}, searchTimestamp={}", dealCount, searchTimestamp);

            // Start Step Functions execution with deal data as input (Req 6.2)
            StartExecutionRequest startRequest = StartExecutionRequest.builder()
                    .stateMachineArn(stateMachineArn)
                    .name("deal-workflow-" + UUID.randomUUID())
                    .input(body)
                    .build();

            StartExecutionResponse response = sfnClient.startExecution(startRequest);
            log.info("Started workflow execution: executionArn={}", response.executionArn());

            // Emit success metric (Req 11.2)
            metricsEmitter.emitWorkflowsStarted(1);

        } catch (Exception e) {
            log.error("Failed to start workflow for SQS message: messageId={}", record.getMessageId(), e);

            // Emit failure metric (Req 11.2)
            metricsEmitter.emitStartFailures(1);

            // Throw so SQS retries the message (Req 6.3)
            throw new RuntimeException("Failed to process deal batch message: " + e.getMessage(), e);
        }
    }
}
