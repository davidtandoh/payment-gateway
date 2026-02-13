package com.checkout.payment.gateway.exception;

/**
 * Thrown when the acquiring bank is unreachable or returns a server error (5xx).
 * Handled by {@link CommonExceptionHandler} to produce a 502 Bad Gateway response.
 */
public class BankUnavailableException extends RuntimeException {

  public BankUnavailableException(String message) {
    super(message);
  }
}
