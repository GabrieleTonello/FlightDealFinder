package com.flightdeal.metrics;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/**
 * Emits custom CloudWatch metrics for the Flight Deal Notifier system.
 * Designed for constructor injection so the CloudWatch client can be mocked in tests.
 */
public class MetricsEmitter {

    private static final String NAMESPACE = "FlightDealNotifier";

    private final CloudWatchClient cloudWatchClient;

    /**
     * Creates a MetricsEmitter with the given CloudWatch client.
     *
     * @param cloudWatchClient the CloudWatch client to use for metric emission
     */
    public MetricsEmitter(CloudWatchClient cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
    }

    /**
     * Emits the number of deals found during a flight search.
     *
     * @param count the number of deals found
     */
    public void emitDealsFound(int count) {
        putMetric("DealsFound", count, StandardUnit.COUNT);
    }

    /**
     * Emits the number of destinations searched during a flight search.
     *
     * @param count the number of destinations searched
     */
    public void emitDestinationsSearched(int count) {
        putMetric("DestinationsSearched", count, StandardUnit.COUNT);
    }

    /**
     * Emits the execution duration of the flight search.
     *
     * @param durationMs the execution duration in milliseconds
     */
    public void emitExecutionDuration(long durationMs) {
        putMetric("ExecutionDuration", durationMs, StandardUnit.MILLISECONDS);
    }

    /**
     * Emits the number of workflows started by the workflow trigger.
     *
     * @param count the number of workflows started
     */
    public void emitWorkflowsStarted(int count) {
        putMetric("WorkflowsStarted", count, StandardUnit.COUNT);
    }

    /**
     * Emits the number of workflow start failures.
     *
     * @param count the number of start failures
     */
    public void emitStartFailures(int count) {
        putMetric("StartFailures", count, StandardUnit.COUNT);
    }

    /**
     * Emits the number of matches found during flight matching.
     *
     * @param count the number of matches found
     */
    public void emitMatchesFound(int count) {
        putMetric("MatchesFound", count, StandardUnit.COUNT);
    }

    /**
     * Emits the number of notifications sent.
     *
     * @param count the number of notifications sent
     */
    public void emitNotificationsSent(int count) {
        putMetric("NotificationsSent", count, StandardUnit.COUNT);
    }

    private void putMetric(String metricName, double value, StandardUnit unit) {
        MetricDatum datum = MetricDatum.builder()
                .metricName(metricName)
                .value(value)
                .unit(unit)
                .build();

        PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(datum)
                .build();

        cloudWatchClient.putMetricData(request);
    }
}
