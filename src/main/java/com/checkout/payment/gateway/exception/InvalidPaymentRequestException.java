package com.checkout.payment.gateway.exception;

import java.util.List;

/**
 * Thrown when a payment request fails validation.
 * Carries a list of all validation errors so they can be reported together.
 * Handled by {@link CommonExceptionHandler} to produce a 400 Rejected response.
 */
public class InvalidPaymentRequestException extends RuntimeException {

  private final List<String> errors;

  public InvalidPaymentRequestException(List<String> errors) {
    super("Invalid payment request: " + String.join(", ", errors));
    this.errors = errors;
  }

  public List<String> getErrors() {
    return errors;
  }
}
