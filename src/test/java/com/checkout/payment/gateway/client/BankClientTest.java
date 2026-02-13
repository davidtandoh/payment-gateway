package com.checkout.payment.gateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@DisplayName("BankClient")
class BankClientTest {

  private BankClientImpl bankClient;
  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.createServer(restTemplate);
    bankClient = new BankClientImpl(restTemplate, "http://localhost:8080");
  }

  private BankPaymentRequest createRequest() {
    BankPaymentRequest request = new BankPaymentRequest();
    request.setCardNumber("12345678901234");
    request.setExpiryDate("12/2025");
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  @Nested
  @DisplayName("Successful Responses")
  class SuccessfulResponses {

    @Test
    @DisplayName("Should parse authorized response from bank")
    void shouldParseAuthorizedResponse() {
      // given
      mockServer.expect(requestTo("http://localhost:8080/payments"))
          .andRespond(withSuccess(
              "{\"authorized\": true, \"authorization_code\": \"auth-code-123\"}",
              MediaType.APPLICATION_JSON));

      // when
      BankPaymentResponse response = bankClient.processPayment(createRequest());

      // then
      assertTrue(response.isAuthorized());
      assertEquals("auth-code-123", response.getAuthorizationCode());
      mockServer.verify();
    }

    @Test
    @DisplayName("Should parse declined response from bank")
    void shouldParseDeclinedResponse() {
      // given
      mockServer.expect(requestTo("http://localhost:8080/payments"))
          .andRespond(withSuccess(
              "{\"authorized\": false, \"authorization_code\": \"\"}",
              MediaType.APPLICATION_JSON));

      // when
      BankPaymentResponse response = bankClient.processPayment(createRequest());

      // then
      assertFalse(response.isAuthorized());
      assertEquals("", response.getAuthorizationCode());
      mockServer.verify();
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Should throw BankUnavailableException when bank returns 503")
    void shouldThrow_whenBankReturns503() {
      // given
      mockServer.expect(requestTo("http://localhost:8080/payments"))
          .andRespond(withServiceUnavailable());

      // when / then
      assertThrows(BankUnavailableException.class,
          () -> bankClient.processPayment(createRequest()));
      mockServer.verify();
    }

    @Test
    @DisplayName("Should throw BankUnavailableException when bank returns 500")
    void shouldThrow_whenBankReturns500() {
      // given
      mockServer.expect(requestTo("http://localhost:8080/payments"))
          .andRespond(withServerError());

      // when / then
      assertThrows(BankUnavailableException.class,
          () -> bankClient.processPayment(createRequest()));
      mockServer.verify();
    }
  }

  @Nested
  @DisplayName("Request Formatting")
  class RequestFormatting {

    @Test
    @DisplayName("Should send correctly formatted JSON to bank")
    void shouldFormatRequestBody_asExpectedJson() {
      // given
      mockServer.expect(requestTo("http://localhost:8080/payments"))
          .andExpect(content().json(
              "{\"card_number\":\"12345678901234\",\"expiry_date\":\"12/2025\","
                  + "\"currency\":\"USD\",\"amount\":100,\"cvv\":\"123\"}"))
          .andRespond(withSuccess(
              "{\"authorized\": true, \"authorization_code\": \"abc\"}",
              MediaType.APPLICATION_JSON));

      // when
      BankPaymentResponse response = bankClient.processPayment(createRequest());

      // then
      assertNotNull(response);
      mockServer.verify();
    }
  }
}
