package com.checkout.payment.gateway.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PaymentRequestValidator")
class PaymentRequestValidatorTest {

  private PaymentRequestValidator validator;

  @BeforeEach
  void setUp() {
    // Fixed clock at January 15, 2025 — all expiry tests are relative to this date
    Clock fixedClock = Clock.fixed(
        Instant.parse("2025-01-15T10:00:00Z"),
        ZoneId.of("UTC"));
    validator = new PaymentRequestValidator(fixedClock);
  }

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("12345678901234");
    request.setExpiryMonth(12);
    request.setExpiryYear(2025);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  @Nested
  @DisplayName("Card Number Validation")
  class CardNumber {

    @Test
    @DisplayName("Should accept 14-digit card number (minimum length)")
    void shouldAccept_whenCardNumberIs14Digits() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCardNumber("12345678901234");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept 19-digit card number (maximum length)")
    void shouldAccept_whenCardNumberIs19Digits() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCardNumber("1234567890123456789");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should reject card number shorter than 14 digits")
    void shouldReject_whenCardNumberIsTooShort() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCardNumber("1234567890123");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Card number must be between 14 and 19 characters"));
    }

    @Test
    @DisplayName("Should reject card number longer than 19 digits")
    void shouldReject_whenCardNumberIsTooLong() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCardNumber("12345678901234567890");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Card number must be between 14 and 19 characters"));
    }

    @Test
    @DisplayName("Should reject card number containing non-numeric characters")
    void shouldReject_whenCardNumberIsNonNumeric() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCardNumber("1234abcd901234");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Card number must contain only digits"));
    }

    @Test
    @DisplayName("Should reject null card number")
    void shouldReject_whenCardNumberIsNull() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCardNumber(null);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Card number is required"));
    }

    @Test
    @DisplayName("Should reject empty card number")
    void shouldReject_whenCardNumberIsEmpty() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCardNumber("");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Card number is required"));
    }
  }

  @Nested
  @DisplayName("Expiry Date Validation")
  class ExpiryDate {

    @Test
    @DisplayName("Should accept expiry in current month (card valid until end of month)")
    void shouldAccept_whenExpiryIsCurrentMonth() {
      // given — clock is Jan 2025
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(1);
      request.setExpiryYear(2025);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept expiry in a future month of the same year")
    void shouldAccept_whenExpiryIsFutureMonthSameYear() {
      // given — clock is Jan 2025, Feb 2025 is future
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(2);
      request.setExpiryYear(2025);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept expiry in a future year")
    void shouldAccept_whenExpiryIsFutureYear() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(1);
      request.setExpiryYear(2030);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept month 1 as valid lower bound")
    void shouldAccept_whenExpiryMonthIs1() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(1);
      request.setExpiryYear(2026);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept month 12 as valid upper bound")
    void shouldAccept_whenExpiryMonthIs12() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(12);
      request.setExpiryYear(2025);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should reject expiry in the previous month")
    void shouldReject_whenExpiryIsPreviousMonth() {
      // given — clock is Jan 2025, Dec 2024 is past
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(12);
      request.setExpiryYear(2024);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Card has expired"));
    }

    @Test
    @DisplayName("Should reject expiry in a past year")
    void shouldReject_whenExpiryIsPastYear() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(6);
      request.setExpiryYear(2023);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Card has expired"));
    }

    @Test
    @DisplayName("Should reject month 0 (below valid range)")
    void shouldReject_whenExpiryMonthIsZero() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(0);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Expiry month must be between 1 and 12"));
    }

    @Test
    @DisplayName("Should reject month 13 (above valid range)")
    void shouldReject_whenExpiryMonthIs13() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(13);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Expiry month must be between 1 and 12"));
    }

    @Test
    @DisplayName("Should reject year 0 (below valid range)")
    void shouldReject_whenExpiryYearIsZero() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryYear(0);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Expiry year must be between 1 and 9999"));
    }

    @Test
    @DisplayName("Should reject year 10000 (above valid range)")
    void shouldReject_whenExpiryYearIsTooHigh() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryYear(10000);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Expiry year must be between 1 and 9999"));
    }

    @Test
    @DisplayName("Should reject null expiry month")
    void shouldReject_whenExpiryMonthIsNull() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(null);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Expiry month is required"));
    }

    @Test
    @DisplayName("Should reject null expiry year")
    void shouldReject_whenExpiryYearIsNull() {
      // given
      PostPaymentRequest request = validRequest();
      request.setExpiryYear(null);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Expiry year is required"));
    }
  }

  @Nested
  @DisplayName("Currency Validation")
  class Currency {

    @Test
    @DisplayName("Should accept USD")
    void shouldAccept_whenCurrencyIsUsd() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCurrency("USD");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept GBP")
    void shouldAccept_whenCurrencyIsGbp() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCurrency("GBP");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept EUR")
    void shouldAccept_whenCurrencyIsEur() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCurrency("EUR");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should reject unsupported currency")
    void shouldReject_whenCurrencyIsUnsupported() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCurrency("JPY");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Currency must be one of: USD, GBP, EUR"));
    }

    @Test
    @DisplayName("Should reject null currency")
    void shouldReject_whenCurrencyIsNull() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCurrency(null);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Currency is required"));
    }
  }

  @Nested
  @DisplayName("Amount Validation")
  class Amount {

    @Test
    @DisplayName("Should accept positive amount")
    void shouldAccept_whenAmountIsPositive() {
      // given
      PostPaymentRequest request = validRequest();
      request.setAmount(1);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should reject zero amount")
    void shouldReject_whenAmountIsZero() {
      // given
      PostPaymentRequest request = validRequest();
      request.setAmount(0);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Amount must be greater than zero"));
    }

    @Test
    @DisplayName("Should reject negative amount")
    void shouldReject_whenAmountIsNegative() {
      // given
      PostPaymentRequest request = validRequest();
      request.setAmount(-5);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Amount must be greater than zero"));
    }

    @Test
    @DisplayName("Should reject null amount")
    void shouldReject_whenAmountIsNull() {
      // given
      PostPaymentRequest request = validRequest();
      request.setAmount(null);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("Amount is required"));
    }
  }

  @Nested
  @DisplayName("CVV Validation")
  class Cvv {

    @Test
    @DisplayName("Should accept 3-digit CVV")
    void shouldAccept_whenCvvIs3Digits() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCvv("123");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept 4-digit CVV")
    void shouldAccept_whenCvvIs4Digits() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCvv("1234");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should accept CVV with leading zeros")
    void shouldAccept_whenCvvHasLeadingZeros() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCvv("0123");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should reject 2-digit CVV (too short)")
    void shouldReject_whenCvvIsTooShort() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCvv("12");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("CVV must be 3 or 4 characters"));
    }

    @Test
    @DisplayName("Should reject 5-digit CVV (too long)")
    void shouldReject_whenCvvIsTooLong() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCvv("12345");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("CVV must be 3 or 4 characters"));
    }

    @Test
    @DisplayName("Should reject non-numeric CVV")
    void shouldReject_whenCvvIsNonNumeric() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCvv("12a");

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("CVV must contain only digits"));
    }

    @Test
    @DisplayName("Should reject null CVV")
    void shouldReject_whenCvvIsNull() {
      // given
      PostPaymentRequest request = validRequest();
      request.setCvv(null);

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.contains("CVV is required"));
    }
  }

  @Nested
  @DisplayName("Multiple Validation Errors")
  class MultipleErrors {

    @Test
    @DisplayName("Should return all errors when multiple fields are invalid")
    void shouldReturnAllErrors_whenMultipleFieldsAreInvalid() {
      // given — all fields null or invalid
      PostPaymentRequest request = new PostPaymentRequest();
      request.setCardNumber(null);
      request.setCurrency(null);
      request.setCvv(null);
      // expiryMonth, expiryYear, amount are null by default (Integer)

      // when
      List<String> errors = validator.validate(request);

      // then
      assertTrue(errors.size() >= 5);
      assertTrue(errors.contains("Card number is required"));
      assertTrue(errors.contains("Expiry year is required"));
      assertTrue(errors.contains("Expiry month is required"));
      assertTrue(errors.contains("Currency is required"));
      assertTrue(errors.contains("Amount is required"));
      assertTrue(errors.contains("CVV is required"));
    }
  }
}
