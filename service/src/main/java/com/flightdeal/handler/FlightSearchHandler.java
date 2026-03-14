package com.flightdeal.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.flightdeal.config.FlightSearchConfig;
import com.flightdeal.dao.PriceRecordDao;
import com.flightdeal.dao.PriceRecordEntity;
import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.CarbonEmissions;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
import com.flightdeal.guice.FlightSearchModule;
import com.flightdeal.metrics.MetricsEmitter;
import com.flightdeal.proxy.FlightApiClient;
import com.flightdeal.proxy.FlightApiException;
import com.flightdeal.proxy.FlightSearchResponse;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Lambda handler for the Flight Search function. Triggered by EventBridge on an hourly schedule.
 *
 * <p>Iterates configured routes, queries the SerpApi Google Flights API per route, writes deals to
 * DynamoDB via PriceRecordDao, publishes the raw response to SNS, and emits CloudWatch metrics.
 *
 * <p>Per-route error isolation ensures one failing route does not block others.
 */
@Slf4j
public class FlightSearchHandler implements RequestHandler<Object, Map<String, Object>> {

  private static final int SNS_MAX_RETRIES = 3;
  private static final long SNS_BASE_DELAY_MS = 100;

  @Inject private FlightApiClient flightApiClient;
  @Inject private PriceRecordDao priceRecordDao;
  @Inject private SnsClient snsClient;
  @Inject private MetricsEmitter metricsEmitter;

  @Inject
  @Named("TOPIC_ARN")
  private String topicArn;

  @Inject private FlightSearchConfig config;

  /**
   * No-arg constructor for Lambda runtime. Creates a Guice injector with FlightSearchModule and
   * injects dependencies.
   */
  public FlightSearchHandler() {
    Guice.createInjector(new FlightSearchModule()).injectMembers(this);
  }

  /** Constructor for dependency injection (used by Guice and tests). */
  @Inject
  public FlightSearchHandler(
      FlightApiClient flightApiClient,
      PriceRecordDao priceRecordDao,
      SnsClient snsClient,
      MetricsEmitter metricsEmitter,
      @Named("TOPIC_ARN") String topicArn,
      FlightSearchConfig config) {
    this.flightApiClient = flightApiClient;
    this.priceRecordDao = priceRecordDao;
    this.snsClient = snsClient;
    this.metricsEmitter = metricsEmitter;
    this.topicArn = topicArn;
    this.config = config;
  }

  @Override
  public Map<String, Object> handleRequest(Object event, Context context) {
    long startTime = System.currentTimeMillis();
    List<PriceRecordEntity> allEntities = new ArrayList<>();
    List<Map<String, String>> errors = new ArrayList<>();
    int totalFlights = 0;

    List<String> routes = config.getSearch().getRoutes();
    String outboundDate = LocalDate.now().plusDays(7).toString();
    String returnDate = LocalDate.now().plusDays(14).toString();

    for (String route : routes) {
      String trimmedRoute = route.trim();
      String[] parts = trimmedRoute.split("-");
      if (parts.length != 2) {
        log.warn("Invalid route format: {}", trimmedRoute);
        continue;
      }
      String departureId = parts[0];
      String arrivalId = parts[1];

      try {
        FlightSearchResponse response =
            flightApiClient.searchFlights(departureId, arrivalId, outboundDate, returnDate);

        List<PriceRecordEntity> entities =
            parseFlights(response, trimmedRoute, departureId, arrivalId, outboundDate, returnDate);
        totalFlights += entities.size();

        if (!entities.isEmpty()) {
          priceRecordDao.saveBatch(entities);
          allEntities.addAll(entities);
        }

        // Publish raw response to SNS
        if (response.hasFlights()) {
          publishRawResponse(response.rawResponse());
        }
      } catch (FlightApiException e) {
        log.warn(
            "Flight API error for route {}: {} [{}]",
            trimmedRoute,
            e.getMessage(),
            e.getErrorType(),
            e);
        Map<String, String> error = new LinkedHashMap<>();
        error.put("route", trimmedRoute);
        error.put("errorMessage", e.getMessage());
        error.put("errorType", e.getErrorType());
        errors.add(error);
      }
    }

    long duration = System.currentTimeMillis() - startTime;
    metricsEmitter.emitDealsFound(totalFlights);
    metricsEmitter.emitDestinationsSearched(routes.size());
    metricsEmitter.emitExecutionDuration(duration);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("dealsFound", totalFlights);
    result.put("routesSearched", routes.size());
    result.put("errorsCount", errors.size());
    result.put("durationMs", duration);
    return result;
  }

  public List<PriceRecordEntity> parseFlights(
      FlightSearchResponse response,
      String route,
      String departureId,
      String arrivalId,
      String outboundDate,
      String returnDate) {
    String timestamp = Instant.now().toString();
    List<PriceRecordEntity> entities = new ArrayList<>();

    for (FlightDeal deal : response.bestFlights()) {
      PriceRecordEntity entity =
          parseFlightNode(deal, route, timestamp, "best", outboundDate, returnDate);
      if (entity != null) {
        entities.add(entity);
      }
    }
    for (FlightDeal deal : response.otherFlights()) {
      PriceRecordEntity entity =
          parseFlightNode(deal, route, timestamp, "other", outboundDate, returnDate);
      if (entity != null) {
        entities.add(entity);
      }
    }
    return entities;
  }

  PriceRecordEntity parseFlightNode(
      FlightDeal deal,
      String route,
      String timestamp,
      String dealType,
      String outboundDate,
      String returnDate) {
    try {
      int price = deal.getPrice();
      int totalDuration = deal.getTotalDuration();

      List<FlightSegment> segments = deal.getFlights();
      if (segments == null || segments.isEmpty()) {
        return null;
      }

      FlightSegment firstSegment = segments.get(0);
      FlightSegment lastSegment = segments.get(segments.size() - 1);

      Airport depAirport =
          firstSegment.getDepartureAirport() != null
              ? firstSegment.getDepartureAirport()
              : Airport.builder().name("").id("").build();
      Airport arrAirport =
          lastSegment.getArrivalAirport() != null
              ? lastSegment.getArrivalAirport()
              : Airport.builder().name("").id("").build();

      String depAirportId = depAirport.getId() != null ? depAirport.getId() : "";
      String depAirportName = depAirport.getName() != null ? depAirport.getName() : "";
      String depTime = depAirport.getTime() != null ? depAirport.getTime() : "";
      String arrAirportId = arrAirport.getId() != null ? arrAirport.getId() : "";
      String arrAirportName = arrAirport.getName() != null ? arrAirport.getName() : "";
      String arrTime = arrAirport.getTime() != null ? arrAirport.getTime() : "";
      String airline = firstSegment.getAirline() != null ? firstSegment.getAirline() : "";
      String flightNumber =
          firstSegment.getFlightNumber() != null ? firstSegment.getFlightNumber() : "";

      Integer carbonEmissionsValue = null;
      CarbonEmissions carbon = deal.getCarbonEmissions();
      if (carbon != null && carbon.getThisFlight() != null) {
        carbonEmissionsValue = carbon.getThisFlight();
      }

      return PriceRecordEntity.builder()
          .route(route)
          .timestamp(timestamp)
          .price(price)
          .departureAirportId(depAirportId)
          .departureAirportName(depAirportName)
          .departureTime(depTime)
          .arrivalAirportId(arrAirportId)
          .arrivalAirportName(arrAirportName)
          .arrivalTime(arrTime)
          .airline(airline)
          .totalDuration(totalDuration)
          .segments(segments.size())
          .flightNumber(flightNumber)
          .dealType(dealType)
          .carbonEmissions(carbonEmissionsValue)
          .outboundDate(outboundDate)
          .returnDate(returnDate)
          .build();
    } catch (Exception e) {
      log.warn("Failed to parse flight node for route {}: {}", route, e.getMessage());
      return null;
    }
  }

  void publishRawResponse(String rawResponse) {
    PublishRequest request =
        PublishRequest.builder().topicArn(topicArn).message(rawResponse).build();
    publishToSnsWithRetry(request);
  }

  /**
   * Publishes to SNS with retry up to SNS_MAX_RETRIES times with exponential backoff. Base delay
   * doubles on each retry: 100ms, 200ms, 400ms.
   */
  void publishToSnsWithRetry(PublishRequest request) {
    int attempt = 0;
    while (true) {
      try {
        snsClient.publish(request);
        return;
      } catch (Exception e) {
        attempt++;
        if (attempt >= SNS_MAX_RETRIES) {
          log.error("SNS publish failed after {} attempts: {}", SNS_MAX_RETRIES, e.getMessage(), e);
          return;
        }
        long delay = SNS_BASE_DELAY_MS * (1L << (attempt - 1));
        log.warn(
            "SNS publish attempt {} failed, retrying in {}ms: {}", attempt, delay, e.getMessage());
        try {
          Thread.sleep(delay);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          log.error("SNS publish retry interrupted", ie);
          return;
        }
      }
    }
  }
}
