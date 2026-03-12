package com.flightdeal.metrics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/** Emits custom CloudWatch metrics for the Flight Deal Notifier system. */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MetricsEmitter {

  private static final String NAMESPACE = "FlightDealNotifier";

  private final CloudWatchClient cloudWatchClient;

  public void emitDealsFound(int count) {
    putMetric("DealsFound", count, StandardUnit.COUNT);
  }

  public void emitDestinationsSearched(int count) {
    putMetric("DestinationsSearched", count, StandardUnit.COUNT);
  }

  public void emitExecutionDuration(long durationMs) {
    putMetric("ExecutionDuration", durationMs, StandardUnit.MILLISECONDS);
  }

  public void emitWorkflowsStarted(int count) {
    putMetric("WorkflowsStarted", count, StandardUnit.COUNT);
  }

  public void emitStartFailures(int count) {
    putMetric("StartFailures", count, StandardUnit.COUNT);
  }

  public void emitMatchesFound(int count) {
    putMetric("MatchesFound", count, StandardUnit.COUNT);
  }

  public void emitNotificationsSent(int count) {
    putMetric("NotificationsSent", count, StandardUnit.COUNT);
  }

  private void putMetric(String metricName, double value, StandardUnit unit) {
    MetricDatum datum =
        MetricDatum.builder().metricName(metricName).value(value).unit(unit).build();

    PutMetricDataRequest request =
        PutMetricDataRequest.builder().namespace(NAMESPACE).metricData(datum).build();

    cloudWatchClient.putMetricData(request);
  }
}
