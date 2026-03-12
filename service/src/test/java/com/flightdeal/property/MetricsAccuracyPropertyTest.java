// Feature: flight-deal-notifier, Property 13: Metrics emission accuracy
package com.flightdeal.property;

import com.flightdeal.metrics.MetricsEmitter;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 13: For any count values, the emitted CloudWatch metric has the correct
 * name, value, and unit.
 *
 * Validates: Requirements 11.1, 11.2, 11.3
 */
class MetricsAccuracyPropertyTest {

    @Property(tries = 100)
    void emittedMetricsMatchActualCounts(@ForAll("metricInput") MetricInput input) {
        CloudWatchClient cloudWatchClient = mock(CloudWatchClient.class);
        MetricsEmitter emitter = new MetricsEmitter(cloudWatchClient);

        // Emit the metric based on the type
        switch (input.metricType) {
            case "DealsFound" -> emitter.emitDealsFound(input.value);
            case "DestinationsSearched" -> emitter.emitDestinationsSearched(input.value);
            case "ExecutionDuration" -> emitter.emitExecutionDuration(input.value);
            case "WorkflowsStarted" -> emitter.emitWorkflowsStarted(input.value);
            case "StartFailures" -> emitter.emitStartFailures(input.value);
            case "MatchesFound" -> emitter.emitMatchesFound(input.value);
            case "NotificationsSent" -> emitter.emitNotificationsSent(input.value);
        }

        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        assertEquals("FlightDealNotifier", request.namespace());

        MetricDatum datum = request.metricData().get(0);
        assertEquals(input.metricType, datum.metricName());
        assertEquals((double) input.value, datum.value(), 0.001);

        StandardUnit expectedUnit = input.metricType.equals("ExecutionDuration")
                ? StandardUnit.MILLISECONDS : StandardUnit.COUNT;
        assertEquals(expectedUnit, datum.unit());
    }

    @Provide
    Arbitrary<MetricInput> metricInput() {
        Arbitrary<String> metricTypes = Arbitraries.of(
                "DealsFound", "DestinationsSearched", "ExecutionDuration",
                "WorkflowsStarted", "StartFailures", "MatchesFound", "NotificationsSent");
        Arbitrary<Integer> values = Arbitraries.integers().between(0, 10000);

        return metricTypes.flatMap(type -> values.map(value -> new MetricInput(type, value)));
    }

    record MetricInput(String metricType, int value) {}
}
