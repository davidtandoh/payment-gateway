package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP-based implementation of {@link BankClient} using {@link RestTemplate}.
 *
 * <p>Posts payment requests to the bank simulator and translates infrastructure
 * failures (HTTP 5xx, timeouts) into {@link BankUnavailableException} so that
 * upstream callers don't need to handle HTTP-level concerns.
 *
 * <p>The bank simulator URL is configured via the {@code bank.simulator.url} property.
 */
@Component
public class BankClientImpl implements BankClient {

  private final RestTemplate restTemplate;
  private final String bankSimulatorUrl;

  public BankClientImpl(RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankSimulatorUrl) {
    this.restTemplate = restTemplate;
    this.bankSimulatorUrl = bankSimulatorUrl;
  }

  @Override
  public BankPaymentResponse processPayment(BankPaymentRequest request) {
    try {
      return restTemplate.postForObject(
          bankSimulatorUrl + "/payments",
          request,
          BankPaymentResponse.class);
    } catch (HttpServerErrorException e) {
      throw new BankUnavailableException("Bank returned error: " + e.getStatusCode());
    } catch (ResourceAccessException e) {
      throw new BankUnavailableException("Bank is unavailable: " + e.getMessage());
    }
  }
}
