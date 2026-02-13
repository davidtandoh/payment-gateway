package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates payment requests before they are sent to the acquiring bank.
 *
 * <p>Uses an explicit validation approach (rather than Bean Validation annotations) to provide:
 * <ul>
 *   <li>All errors returned at once, not just the first failure</li>
 *   <li>Full control over error messages</li>
 *   <li>Testable date logic via injected {@link Clock}</li>
 * </ul>
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Card number: required, 14-19 digits, numeric only</li>
 *   <li>Expiry: month 1-12, must not be in the past (current month is valid)</li>
 *   <li>Currency: required, must be one of USD, GBP, EUR</li>
 *   <li>Amount: must be a positive integer</li>
 *   <li>CVV: required, 3-4 digits, numeric only</li>
 * </ul>
 */
@Component
public class PaymentRequestValidator {

  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "GBP", "EUR");
  private final Clock clock;

  public PaymentRequestValidator(Clock clock) {
    this.clock = clock;
  }

  /**
   * Validates the given payment request against all rules.
   *
   * @param request the payment request to validate
   * @return a list of human-readable error messages; empty if the request is valid
   */
  public List<String> validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    validateCardNumber(request.getCardNumber(), errors);
    validateExpiryDate(request.getExpiryMonth(), request.getExpiryYear(), errors);
    validateCurrency(request.getCurrency(), errors);
    validateAmount(request.getAmount(), errors);
    validateCvv(request.getCvv(), errors);

    return errors;
  }

  private void validateCardNumber(String cardNumber, List<String> errors) {
    if (cardNumber == null || cardNumber.isBlank()) {
      errors.add("Card number is required");
      return;
    }
    if (cardNumber.length() < 14 || cardNumber.length() > 19) {
      errors.add("Card number must be between 14 and 19 characters");
    }
    if (!cardNumber.matches("\\d+")) {
      errors.add("Card number must contain only digits");
    }
  }

  /**
   * Validates that the expiry date is not in the past. A card expiring in the current
   * month is considered valid (the card is usable until the last day of that month).
   */
  private void validateExpiryDate(Integer expiryMonth, Integer expiryYear, List<String> errors) {
    if (expiryYear == null) {
      errors.add("Expiry year is required");
    } else if (expiryYear < 1 || expiryYear > 9999) {
      errors.add("Expiry year must be between 1 and 9999");
    }
    if (expiryMonth == null) {
      errors.add("Expiry month is required");
    } else if (expiryMonth < 1 || expiryMonth > 12) {
      errors.add("Expiry month must be between 1 and 12");
    }
    if (expiryMonth == null || expiryYear == null) {
      return;
    }
    if (expiryYear < 1 || expiryYear > 9999 || expiryMonth < 1 || expiryMonth > 12) {
      return;
    }
    LocalDate now = LocalDate.now(clock);
    LocalDate expiryDate = LocalDate.of(expiryYear, expiryMonth, 1)
        .plusMonths(1)
        .minusDays(1);
    if (expiryDate.isBefore(now)) {
      errors.add("Card has expired");
    }
  }

  private void validateCurrency(String currency, List<String> errors) {
    if (currency == null || currency.isBlank()) {
      errors.add("Currency is required");
      return;
    }
    if (!SUPPORTED_CURRENCIES.contains(currency)) {
      errors.add("Currency must be one of: USD, GBP, EUR");
    }
  }

  private void validateAmount(Integer amount, List<String> errors) {
    if (amount == null) {
      errors.add("Amount is required");
      return;
    }
    if (amount <= 0) {
      errors.add("Amount must be greater than zero");
    }
  }

  private void validateCvv(String cvv, List<String> errors) {
    if (cvv == null || cvv.isBlank()) {
      errors.add("CVV is required");
      return;
    }
    if (cvv.length() < 3 || cvv.length() > 4) {
      errors.add("CVV must be 3 or 4 characters");
    }
    if (!cvv.matches("\\d+")) {
      errors.add("CVV must contain only digits");
    }
  }
}
