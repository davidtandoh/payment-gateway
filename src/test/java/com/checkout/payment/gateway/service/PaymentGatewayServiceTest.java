package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.InvalidPaymentRequestException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("PaymentGatewayService")
@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;
  @Mock
  private PaymentRequestValidator validator;
  @Mock
  private BankClient bankClient;
  @Mock
  private IdempotencyRepository idempotencyRepository;

  private PaymentGatewayServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new PaymentGatewayServiceImpl(paymentsRepository, validator, bankClient,
        idempotencyRepository);
  }

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("12345678901235");
    request.setExpiryMonth(12);
    request.setExpiryYear(2025);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  private BankPaymentResponse authorizedBankResponse() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(true);
    response.setAuthorizationCode("auth-123");
    return response;
  }

  private BankPaymentResponse declinedBankResponse() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(false);
    response.setAuthorizationCode("");
    return response;
  }

  @Nested
  @DisplayName("Payment Processing")
  class PaymentProcessing {

    @Test
    @DisplayName("Should return Authorized status when bank approves payment")
    void shouldReturnAuthorized_whenBankApproves() {
      // given
      when(validator.validate(any())).thenReturn(Collections.emptyList());
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());

      // when
      PostPaymentResponse response = service.processPayment(validRequest(), null);

      // then
      assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
      assertNotNull(response.getId());
      verify(paymentsRepository).add(response);
    }

    @Test
    @DisplayName("Should return Declined status when bank declines payment")
    void shouldReturnDeclined_whenBankDeclines() {
      // given
      when(validator.validate(any())).thenReturn(Collections.emptyList());
      when(bankClient.processPayment(any())).thenReturn(declinedBankResponse());

      // when
      PostPaymentResponse response = service.processPayment(validRequest(), null);

      // then
      assertEquals(PaymentStatus.DECLINED, response.getStatus());
      verify(paymentsRepository).add(response);
    }

    @Test
    @DisplayName("Should map request fields correctly to response")
    void shouldMapRequestFields_toResponse() {
      // given
      when(validator.validate(any())).thenReturn(Collections.emptyList());
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());
      PostPaymentRequest request = validRequest();

      // when
      PostPaymentResponse response = service.processPayment(request, null);

      // then
      assertEquals(request.getExpiryMonth(), response.getExpiryMonth());
      assertEquals(request.getExpiryYear(), response.getExpiryYear());
      assertEquals(request.getCurrency(), response.getCurrency());
      assertEquals(request.getAmount(), response.getAmount());
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Should throw InvalidPaymentRequestException and never call bank when validation fails")
    void shouldThrowAndSkipBank_whenValidationFails() {
      // given
      when(validator.validate(any())).thenReturn(List.of("Card number is required"));

      // when / then
      assertThrows(InvalidPaymentRequestException.class,
          () -> service.processPayment(validRequest(), null));

      verify(bankClient, never()).processPayment(any());
      verify(paymentsRepository, never()).add(any());
    }

    @Test
    @DisplayName("Should propagate BankUnavailableException and store nothing")
    void shouldPropagateException_whenBankIsUnavailable() {
      // given
      when(validator.validate(any())).thenReturn(Collections.emptyList());
      when(bankClient.processPayment(any())).thenThrow(
          new BankUnavailableException("unavailable"));

      // when / then
      assertThrows(BankUnavailableException.class,
          () -> service.processPayment(validRequest(), null));

      verify(paymentsRepository, never()).add(any());
    }
  }

  @Nested
  @DisplayName("Card Data Security")
  class CardDataSecurity {

    @Test
    @DisplayName("Should store only last 4 digits of card number in response")
    void shouldStoreOnlyLastFour_whenPaymentIsProcessed() {
      // given
      when(validator.validate(any())).thenReturn(Collections.emptyList());
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());
      PostPaymentRequest request = validRequest();
      request.setCardNumber("12345678901235");

      // when
      service.processPayment(request, null);

      // then
      ArgumentCaptor<PostPaymentResponse> captor = ArgumentCaptor.forClass(
          PostPaymentResponse.class);
      verify(paymentsRepository).add(captor.capture());

      PostPaymentResponse stored = captor.getValue();
      assertEquals("1235", stored.getCardNumberLastFour());
      assertTrue(stored.getCardNumberLastFour().length() == 4);
    }

    @Test
    @DisplayName("Should extract last 4 digits correctly from different card numbers")
    void shouldExtractLastFour_fromDifferentCardNumbers() {
      // given
      when(validator.validate(any())).thenReturn(Collections.emptyList());
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());
      PostPaymentRequest request = validRequest();
      request.setCardNumber("4111111111114321");

      // when
      PostPaymentResponse response = service.processPayment(request, null);

      // then
      assertEquals("4321", response.getCardNumberLastFour());
    }
  }

  @Nested
  @DisplayName("Bank Request Formatting")
  class BankRequestFormatting {

    @Test
    @DisplayName("Should format expiry date as MM/YYYY for bank request")
    void shouldFormatExpiryDate_asMMSlashYYYY() {
      // given
      when(validator.validate(any())).thenReturn(Collections.emptyList());
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());
      PostPaymentRequest request = validRequest();
      request.setExpiryMonth(1);
      request.setExpiryYear(2026);

      // when
      service.processPayment(request, null);

      // then
      ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(
          BankPaymentRequest.class);
      verify(bankClient).processPayment(captor.capture());
      assertEquals("01/2026", captor.getValue().getExpiryDate());
    }
  }

  @Nested
  @DisplayName("Input Sanitization")
  class InputSanitization {

    @Test
    @DisplayName("Should trim whitespace from card number and CVV before validation")
    void shouldTrimWhitespace_fromCardNumberAndCvv() {
      // given
      when(validator.validate(any())).thenReturn(Collections.emptyList());
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());
      PostPaymentRequest request = validRequest();
      request.setCardNumber("  12345678901235  ");
      request.setCvv(" 123 ");

      // when
      service.processPayment(request, null);

      // then
      assertEquals("12345678901235", request.getCardNumber());
      assertEquals("123", request.getCvv());
    }
  }
}
