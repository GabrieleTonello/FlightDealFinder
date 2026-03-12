package com.flightdeal.proxy;

import lombok.Getter;

/**
 * Exception thrown when the external flight API returns an error or times out. Carries the
 * destination, error message, and error type for structured error reporting.
 */
@Getter
public class FlightApiException extends Exception {

  private final String destination;
  private final String errorType;

  public FlightApiException(String destination, String message, String errorType) {
    super(message);
    this.destination = destination;
    this.errorType = errorType;
  }
}
