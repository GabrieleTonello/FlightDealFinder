package com.flightdeal.proxy;

import lombok.Getter;

/**
 * Exception thrown when the Google Calendar API returns an error or is unavailable. Carries the
 * error message and error type for structured error reporting.
 */
@Getter
public class CalendarApiException extends Exception {

  private final String errorType;

  public CalendarApiException(String message, String errorType) {
    super(message);
    this.errorType = errorType;
  }
}
