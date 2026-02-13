package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory store mapping idempotency keys to their original payment responses.
 *
 * <p>Uses {@link ConcurrentHashMap} for thread safety. When a merchant provides
 * the same {@code Idempotency-Key} header on a repeated POST request, the service
 * returns the cached response without reprocessing the payment through the bank.
 *
 * <p>Note: keys are stored indefinitely in this implementation. A production system
 * should add TTL-based expiration to prevent unbounded memory growth.
 */
@Repository
public class IdempotencyRepository {

  private final ConcurrentHashMap<String, PostPaymentResponse> store = new ConcurrentHashMap<>();

  public Optional<PostPaymentResponse> find(String key) {
    return Optional.ofNullable(store.get(key));
  }

  public void store(String key, PostPaymentResponse response) {
    store.put(key, response);
  }
}
