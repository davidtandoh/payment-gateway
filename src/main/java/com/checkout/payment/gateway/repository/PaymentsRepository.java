package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * In-memory store for processed payment records.
 *
 * <p>Only stores {@link PostPaymentResponse} objects which contain masked card data
 * (last 4 digits only). The full card number is never persisted here.
 *
 * <p>Note: this is a non-persistent implementation suitable for the coding challenge.
 * A production system would use a database.
 */
@Repository
public class PaymentsRepository {

  private final HashMap<UUID, PostPaymentResponse> payments = new HashMap<>();

  public void add(PostPaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  public Optional<PostPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }
}
