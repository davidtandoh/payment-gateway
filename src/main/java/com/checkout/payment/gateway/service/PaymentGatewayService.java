package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.UUID;

/**
 * Defines the contract for payment processing operations.
 *
 * <p>Implementations handle the full lifecycle: validation, bank communication,
 * response mapping, and storage. The interface allows the controller to depend
 * on an abstraction rather than a concrete implementation (Dependency Inversion Principle).
 */
public interface PaymentGatewayService {

  /**
   * Retrieves a previously processed payment by ID.
   *
   * @param id the payment UUID
   * @return the stored payment response
   * @throws com.checkout.payment.gateway.exception.EventProcessingException if no payment exists
   */
  PostPaymentResponse getPaymentById(UUID id);

  /**
   * Processes a payment request through the acquiring bank.
   *
   * @param request the merchant's payment request
   * @param idempotencyKey optional key to prevent duplicate processing; may be null
   * @return the payment result including a generated ID and AUTHORIZED/DECLINED status
   * @throws com.checkout.payment.gateway.exception.InvalidPaymentRequestException if validation fails
   * @throws com.checkout.payment.gateway.exception.BankUnavailableException if the bank is unreachable
   */
  PostPaymentResponse processPayment(PostPaymentRequest request, String idempotencyKey);
}
