package com.checkout.payment.gateway.exception;

/**
 * Thrown when a requested resource cannot be found or a processing error occurs.
 * Handled by {@link CommonExceptionHandler} to produce a 404 Not Found response
 * using the exception's own message.
 */
public class EventProcessingException extends RuntimeException {

  public EventProcessingException(String message) {
    super(message);
  }
}
