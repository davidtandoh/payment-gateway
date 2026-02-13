package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global exception handler that maps domain exceptions to HTTP responses.
 *
 * <p>Each handler uses the exception's own message via {@code ex.getMessage()}
 * so the thrower controls the response content, not the handler.
 *
 * <ul>
 *   <li>{@link EventProcessingException} -> 404 Not Found</li>
 *   <li>{@link InvalidPaymentRequestException} -> 400 Bad Request with status "Rejected"</li>
 *   <li>{@link BankUnavailableException} -> 502 Bad Gateway</li>
 * </ul>
 */
@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Processing error", ex);
    return new ResponseEntity<>(new ErrorResponse(ex.getMessage()),
        HttpStatus.NOT_FOUND);
  }

  /** Returns a 400 with validation errors when the request is invalid. */
  @ExceptionHandler(InvalidPaymentRequestException.class)
  public ResponseEntity<ErrorResponse> handleInvalidPaymentRequest(
      InvalidPaymentRequestException ex) {
    LOG.warn("Invalid payment request: {}", ex.getErrors());
    return new ResponseEntity<>(new ErrorResponse(ex.getMessage()),
        HttpStatus.BAD_REQUEST);
  }

  /** Returns a 502 when the bank simulator is unreachable or returns 5xx. */
  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(BankUnavailableException ex) {
    LOG.error("Bank unavailable", ex);
    return new ResponseEntity<>(new ErrorResponse(ex.getMessage()),
        HttpStatus.BAD_GATEWAY);
  }

  /** Returns a 400 when the request body cannot be parsed (e.g. unknown fields, malformed JSON). */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
    LOG.warn("Malformed request body: {}", ex.getMessage());
    String message = extractReadableMessage(ex);
    return new ResponseEntity<>(new ErrorResponse(message), HttpStatus.BAD_REQUEST);
  }

  private String extractReadableMessage(HttpMessageNotReadableException ex) {
    String detail = ex.getMostSpecificCause().getMessage();
    if (detail != null && detail.contains("Unrecognized field")) {
      int start = detail.indexOf('"');
      int end = detail.indexOf('"', start + 1);
      if (start >= 0 && end > start) {
        return "Unrecognized field: '" + detail.substring(start + 1, end) + "'";
      }
    }
    return "Malformed request body";
  }
}
