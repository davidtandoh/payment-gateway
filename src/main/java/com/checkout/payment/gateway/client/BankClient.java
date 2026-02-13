package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;

/**
 * Abstraction for communicating with the acquiring bank's payment API.
 *
 * <p>Decouples the service layer from HTTP transport details, allowing
 * alternative implementations (e.g. for testing or switching banks)
 * without modifying the service (Dependency Inversion Principle).
 */
public interface BankClient {

  /**
   * Sends a payment request to the bank for authorization.
   *
   * @param request the bank-formatted payment request
   * @return the bank's response indicating authorization or decline
   * @throws com.checkout.payment.gateway.exception.BankUnavailableException if the bank is
   *     unreachable or returns a server error
   */
  BankPaymentResponse processPayment(BankPaymentRequest request);
}
