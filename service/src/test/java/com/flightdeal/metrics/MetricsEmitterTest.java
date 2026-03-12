package com.flightdeal.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MetricsEmitter.
 * Validates: Requirements 11.1, 11.2, 11.3, 17.1, 17.5
 */
@ExtendWith(MockitoExtension.class)
class MetricsEmitterTest {

    @Mock
    private CloudWatchClient cloudWatchClient;

    private MetricsEmitter metricsEmitter;

    @BeforeEach
    void setUp() {
        metricsEmitter = new MetricsEmitter(cloudWatchClient);
    }

    private void verifyMetric(String expectedName, double expectedValue, StandardUnit expectedUnit) {
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        assertEquals("FlightDealNotifier", request.namespace());

        MetricDatum datum = request.metricData().get(0);
        assertEquals(expectedName, datum.metricName());
        assertEquals(expectedValue, datum.value());
        assertEquals(expectedUnit, datum.unit());
    }

    @Test
    void emitDealsFound_putsCorrectMetric() {
        metricsEmitter.emitDealsFound(42);
        verifyMetric("DealsFound", 42.0, StandardUnit.COUNT);
    }

    @Test
    void emitDestinationsSearched_putsCorrectMetric() {
        metricsEmitter.emitDestinationsSearched(5);
        verifyMetric("DestinationsSearched", 5.0, StandardUnit.COUNT);
    }

    @Test
    void emitExecutionDuration_putsCorrectMetric() {
        metricsEmitter.emitExecutionDuration(1500L);
        verifyMetric("ExecutionDuration", 1500.0, StandardUnit.MILLISECONDS);
    }

    @Test
    void emitWorkflowsStarted_putsCorrectMetric() {
        metricsEmitter.emitWorkflowsStarted(3);
        verifyMetric("WorkflowsStarted", 3.0, StandardUnit.COUNT);
    }

    @Test
    void emitStartFailures_putsCorrectMetric() {
        metricsEmitter.emitStartFailures(1);
        verifyMetric("StartFailures", 1.0, StandardUnit.COUNT);
    }

    @Test
    void emitMatchesFound_putsCorrectMetric() {
        metricsEmitter.emitMatchesFound(7);
        verifyMetric("MatchesFound", 7.0, StandardUnit.COUNT);
    }

    @Test
    void emitNotificationsSent_putsCorrectMetric() {
        metricsEmitter.emitNotificationsSent(2);
        verifyMetric("NotificationsSent", 2.0, StandardUnit.COUNT);
    }
}
