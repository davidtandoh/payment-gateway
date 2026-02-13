package com.checkout.payment.gateway.configuration;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Application-wide bean configuration.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link RestTemplate} with 10-second connect/read timeouts for bank communication</li>
 *   <li>{@link Clock} for testable date/time logic (can be replaced with a fixed clock in tests)</li>
 * </ul>
 */
@Configuration
public class ApplicationConfiguration {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofMillis(10000))
        .setReadTimeout(Duration.ofMillis(10000))
        .build();
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
