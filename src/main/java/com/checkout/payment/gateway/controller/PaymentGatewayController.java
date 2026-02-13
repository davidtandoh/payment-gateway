package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing payment endpoints for merchants.
 *
 * <p>Provides two operations:
 * <ul>
 *   <li>POST /api/v1/payments - Submit a new payment for processing</li>
 *   <li>GET /api/v1/payments/{id} - Retrieve a previously processed payment by its ID</li>
 * </ul>
 *
 * <p>The POST endpoint accepts an optional {@code Idempotency-Key} header.
 * When provided, repeated requests with the same key return the original response
 * without reprocessing the payment through the bank.
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  /**
   * Retrieves a previously processed payment by its unique identifier.
   *
   * @param id the payment UUID assigned during processing
   * @return the payment details (masked card number, status, amount, currency)
   * @throws com.checkout.payment.gateway.exception.EventProcessingException if no payment exists
   *     with the given ID (results in 404)
   */
  @GetMapping("/payments/{id}")
  public ResponseEntity<PostPaymentResponse> getPaymentById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

  /**
   * Processes a new card payment through the acquiring bank.
   *
   * <p>The request is validated, forwarded to the bank simulator, and the result
   * is stored for later retrieval. The full card number is never persisted - only
   * the last 4 digits appear in the response and storage.
   *
   * @param request the payment details including full card number, expiry, currency, amount, CVV
   * @param idempotencyKey optional header to prevent duplicate payment processing
   * @return the payment result with status (Authorized/Declined), masked card, and payment ID
   */
  @PostMapping("/payments")
  public ResponseEntity<PostPaymentResponse> processPayment(
      @RequestBody PostPaymentRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    PostPaymentResponse response = paymentGatewayService.processPayment(request, idempotencyKey);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }
}
