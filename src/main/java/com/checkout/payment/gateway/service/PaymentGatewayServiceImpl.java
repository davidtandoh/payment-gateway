package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.InvalidPaymentRequestException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.IdempotencyRepository;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Core service implementation orchestrating the payment processing flow.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Idempotency check - returns cached response for duplicate requests</li>
 *   <li>Input sanitization - trims whitespace from card number and CVV</li>
 *   <li>Validation - delegates to {@link PaymentRequestValidator}</li>
 *   <li>Bank communication - delegates to {@link BankClient}</li>
 *   <li>Response mapping - extracts last 4 digits, maps bank result to payment status</li>
 *   <li>Storage - persists masked payment data for later retrieval</li>
 * </ul>
 *
 * <p>Security: the full card number and CVV are never persisted or returned.
 * Only the last 4 digits of the card number are stored in the response object.
 *
 * <p>Observability: structured log events are emitted at each stage of processing.
 * Payment ID and masked card number are added to MDC for correlation across log lines.
 * Key business metrics logged:
 * <ul>
 *   <li>{@code payment.idempotency_hit} — cached response returned</li>
 *   <li>{@code payment.validation_failed} — request rejected with error count</li>
 *   <li>{@code payment.bank_called} — bank request sent with timing context</li>
 *   <li>{@code payment.processed} — final outcome with status, currency, amount</li>
 * </ul>
 */
@Service
public class PaymentGatewayServiceImpl implements PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayServiceImpl.class);

  private final PaymentsRepository paymentsRepository;
  private final PaymentRequestValidator validator;
  private final BankClient bankClient;
  private final IdempotencyRepository idempotencyRepository;

  public PaymentGatewayServiceImpl(PaymentsRepository paymentsRepository,
      PaymentRequestValidator validator,
      BankClient bankClient,
      IdempotencyRepository idempotencyRepository) {
    this.paymentsRepository = paymentsRepository;
    this.validator = validator;
    this.bankClient = bankClient;
    this.idempotencyRepository = idempotencyRepository;
  }

  @Override
  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting payment with ID {}", id);
    return paymentsRepository.get(id)
        .orElseThrow(() -> new EventProcessingException(
            "Payment not found with ID: " + id));
  }

  @Override
  public PostPaymentResponse processPayment(PostPaymentRequest request, String idempotencyKey) {
    sanitizeRequest(request);

    if (idempotencyKey != null) {
      Optional<PostPaymentResponse> cached = idempotencyRepository.find(idempotencyKey);
      if (cached.isPresent()) {
        LOG.info("event=payment.idempotency_hit idempotencyKey={}", idempotencyKey);
        return cached.get();
      }
    }

    LOG.info("event=payment.received amount={} currency={}",
        request.getAmount(), request.getCurrency());

    List<String> errors = validator.validate(request);
    if (!errors.isEmpty()) {
      LOG.warn("event=payment.validation_failed errorCount={} errors={}",
          errors.size(), errors);
      throw new InvalidPaymentRequestException(errors);
    }

    String cardLastFour = request.getCardNumber()
        .substring(request.getCardNumber().length() - 4);
    MDC.put("cardLastFour", cardLastFour);

    BankPaymentRequest bankRequest = buildBankRequest(request);

    long bankStart = System.currentTimeMillis();
    BankPaymentResponse bankResponse = bankClient.processPayment(bankRequest);
    long bankDuration = System.currentTimeMillis() - bankStart;

    LOG.info("event=payment.bank_responded authorized={} bankLatencyMs={}",
        bankResponse.isAuthorized(), bankDuration);

    PostPaymentResponse response = buildResponse(request, bankResponse);
    MDC.put("paymentId", response.getId().toString());

    paymentsRepository.add(response);

    if (idempotencyKey != null) {
      idempotencyRepository.store(idempotencyKey, response);
    }

    LOG.info("event=payment.processed status={} amount={} currency={} cardLastFour={}",
        response.getStatus(), response.getAmount(), response.getCurrency(), cardLastFour);

    return response;
  }

  /**
   * Trims leading/trailing whitespace from card number and CVV to handle
   * accidental spaces in merchant input.
   */
  private void sanitizeRequest(PostPaymentRequest request) {
    if (request.getCardNumber() != null) {
      request.setCardNumber(request.getCardNumber().trim());
    }
    if (request.getCvv() != null) {
      request.setCvv(request.getCvv().trim());
    }
  }

  private BankPaymentRequest buildBankRequest(PostPaymentRequest request) {
    BankPaymentRequest bankRequest = new BankPaymentRequest();
    bankRequest.setCardNumber(request.getCardNumber());
    bankRequest.setExpiryDate(
        String.format("%02d/%d", request.getExpiryMonth(), request.getExpiryYear()));
    bankRequest.setCurrency(request.getCurrency());
    bankRequest.setAmount(request.getAmount());
    bankRequest.setCvv(request.getCvv());
    return bankRequest;
  }

  private PostPaymentResponse buildResponse(PostPaymentRequest request,
      BankPaymentResponse bankResponse) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(
        bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
    response.setCardNumberLastFour(
        request.getCardNumber().substring(request.getCardNumber().length() - 4));
    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());
    return response;
  }
}
