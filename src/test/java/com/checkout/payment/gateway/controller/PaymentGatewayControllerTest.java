package com.checkout.payment.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@DisplayName("PaymentGatewayController")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  private BankClient bankClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private String validPaymentJson() {
    return """
        {
          "card_number": "2222405343248877",
          "expiry_month": 4,
          "expiry_year": 2026,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;
  }

  private String validPaymentJsonDeclined() {
    return """
        {
          "card_number": "2222405343248878",
          "expiry_month": 4,
          "expiry_year": 2026,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;
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
  @DisplayName("GET /api/v1/payments/{id}")
  class GetPayment {

    @Test
    @DisplayName("Should return payment details when payment exists")
    void shouldReturnPayment_whenPaymentExists() throws Exception {
      // given
      PostPaymentResponse payment = new PostPaymentResponse();
      payment.setId(UUID.randomUUID());
      payment.setAmount(10);
      payment.setCurrency("USD");
      payment.setStatus(PaymentStatus.AUTHORIZED);
      payment.setExpiryMonth(12);
      payment.setExpiryYear(2024);
      payment.setCardNumberLastFour("4321");
      paymentsRepository.add(payment);

      // when / then
      mvc.perform(MockMvcRequestBuilders.get("/api/v1/payments/" + payment.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
          .andExpect(jsonPath("$.card_number_last_four").value(payment.getCardNumberLastFour()))
          .andExpect(jsonPath("$.expiry_month").value(payment.getExpiryMonth()))
          .andExpect(jsonPath("$.expiry_year").value(payment.getExpiryYear()))
          .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
          .andExpect(jsonPath("$.amount").value(payment.getAmount()));
    }

    @Test
    @DisplayName("Should return 404 when payment does not exist")
    void shouldReturn404_whenPaymentDoesNotExist() throws Exception {
      // given — random UUID not in repository

      // when / then
      mvc.perform(MockMvcRequestBuilders.get("/api/v1/payments/" + UUID.randomUUID()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.message", startsWith("Payment not found")));
    }

    @Test
    @DisplayName("Should return 400 when ID is not a valid UUID")
    void shouldReturn400_whenIdIsNotValidUuid() throws Exception {
      // given — invalid UUID format

      // when / then
      mvc.perform(MockMvcRequestBuilders.get("/api/v1/payments/not-a-uuid"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("POST /api/v1/payments - Happy Path")
  class PostPaymentHappyPath {

    @Test
    @DisplayName("Should return 200 with Authorized status when bank approves")
    void shouldReturnAuthorized_whenBankApproves() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("Authorized"))
          .andExpect(jsonPath("$.card_number_last_four").value("8877"))
          .andExpect(jsonPath("$.expiry_month").value(4))
          .andExpect(jsonPath("$.expiry_year").value(2026))
          .andExpect(jsonPath("$.currency").value("GBP"))
          .andExpect(jsonPath("$.amount").value(100))
          .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("Should return 200 with Declined status when bank declines")
    void shouldReturnDeclined_whenBankDeclines() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(declinedBankResponse());

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJsonDeclined()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("Declined"))
          .andExpect(jsonPath("$.card_number_last_four").value("8878"));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/payments - Validation Errors")
  class PostPaymentValidation {

    @Test
    @DisplayName("Should return 400 when card number is too short")
    void shouldReturn400_whenCardNumberIsTooShort() throws Exception {
      // given
      String json = """
          {
            "card_number": "1234567890123",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 100,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when card number is too long")
    void shouldReturn400_whenCardNumberIsTooLong() throws Exception {
      // given
      String json = """
          {
            "card_number": "12345678901234567890",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 100,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when card number contains non-numeric characters")
    void shouldReturn400_whenCardNumberIsNonNumeric() throws Exception {
      // given
      String json = """
          {
            "card_number": "1234abcd901234",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 100,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when card number is missing")
    void shouldReturn400_whenCardNumberIsMissing() throws Exception {
      // given
      String json = """
          {
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 100,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("Card number is required")));
    }

    @Test
    @DisplayName("Should return 400 when expiry date is in the past")
    void shouldReturn400_whenExpiryDateIsInThePast() throws Exception {
      // given
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 1,
            "expiry_year": 2020,
            "currency": "GBP",
            "amount": 100,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when expiry month is out of range")
    void shouldReturn400_whenExpiryMonthIsOutOfRange() throws Exception {
      // given
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 13,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 100,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when currency is unsupported")
    void shouldReturn400_whenCurrencyIsUnsupported() throws Exception {
      // given
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "JPY",
            "amount": 100,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when amount is zero")
    void shouldReturn400_whenAmountIsZero() throws Exception {
      // given
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 0,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when amount is negative")
    void shouldReturn400_whenAmountIsNegative() throws Exception {
      // given
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": -5,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when CVV is too short")
    void shouldReturn400_whenCvvIsTooShort() throws Exception {
      // given
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 100,
            "cvv": "12"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when CVV is too long")
    void shouldReturn400_whenCvvIsTooLong() throws Exception {
      // given
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 100,
            "cvv": "12345"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 when CVV is missing")
    void shouldReturn400_whenCvvIsMissing() throws Exception {
      // given
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "GBP",
            "amount": 100
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("CVV is required")));
    }

    @Test
    @DisplayName("Should return all validation errors when multiple fields are invalid")
    void shouldReturnAllErrors_whenMultipleFieldsAreInvalid() throws Exception {
      // given
      String json = """
          {
            "card_number": "123",
            "expiry_month": 13,
            "expiry_year": 2026,
            "currency": "XYZ",
            "amount": -1,
            "cvv": "1"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(
              containsString("Card number must be between 14 and 19 characters")))
          .andExpect(jsonPath("$.message").value(
              containsString("Expiry month must be between 1 and 12")))
          .andExpect(jsonPath("$.message").value(
              containsString("Amount must be greater than zero")));
    }

    @Test
    @DisplayName("Should return 400 when request body is empty JSON")
    void shouldReturn400_whenBodyIsEmptyJson() throws Exception {
      // given — empty JSON object

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 with field name when request contains unknown property")
    void shouldReturn400_whenRequestContainsUnknownProperty() throws Exception {
      // given — "expiry_yea" is a typo for "expiry_year"
      String json = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 4,
            "expiry_yea": 2026,
            "currency": "GBP",
            "amount": 100,
            "cvv": "123"
          }
          """;

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("expiry_yea")));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/payments - Bank Errors")
  class PostPaymentBankErrors {

    @Test
    @DisplayName("Should return 502 when bank is unavailable")
    void shouldReturn502_whenBankIsUnavailable() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenThrow(
          new BankUnavailableException("Bank is unavailable"));

      // when / then
      mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJson()))
          .andExpect(status().isBadGateway())
          .andExpect(jsonPath("$.message").value("Bank is unavailable"));
    }
  }

  @Nested
  @DisplayName("Card Data Security")
  class CardDataSecurity {

    @Test
    @DisplayName("Should never include full card number in response")
    void shouldNeverIncludeFullCardNumber_inResponse() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());

      // when
      MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      // then
      String responseBody = result.getResponse().getContentAsString();
      assertFalse(responseBody.contains("2222405343248877"),
          "Response should not contain full card number");
    }

    @Test
    @DisplayName("Should never include CVV in response")
    void shouldNeverIncludeCvv_inResponse() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());

      // when
      MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      // then
      String responseBody = result.getResponse().getContentAsString();
      assertFalse(responseBody.contains("\"cvv\""),
          "Response should not contain CVV field");
    }
  }

  @Nested
  @DisplayName("Round-trip (POST then GET)")
  class RoundTrip {

    @Test
    @DisplayName("Should return same data when retrieving a previously created payment")
    void shouldReturnSameData_whenRetrievingCreatedPayment() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());

      // when — create payment
      MvcResult postResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      String id = objectMapper
          .readTree(postResult.getResponse().getContentAsString())
          .get("id").asText();

      // then — retrieve and verify
      mvc.perform(MockMvcRequestBuilders.get("/api/v1/payments/" + id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id))
          .andExpect(jsonPath("$.status").value("Authorized"))
          .andExpect(jsonPath("$.card_number_last_four").value("8877"))
          .andExpect(jsonPath("$.currency").value("GBP"))
          .andExpect(jsonPath("$.amount").value(100));
    }
  }

  @Nested
  @DisplayName("Idempotency")
  class Idempotency {

    @Test
    @DisplayName("Should return same response and call bank only once for duplicate idempotency key")
    void shouldReturnCachedResponse_whenSameIdempotencyKey() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());
      String idempotencyKey = UUID.randomUUID().toString();

      // when
      MvcResult firstResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .header("Idempotency-Key", idempotencyKey)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      MvcResult secondResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .header("Idempotency-Key", idempotencyKey)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      // then
      assertEquals(firstResult.getResponse().getContentAsString(),
          secondResult.getResponse().getContentAsString());
      verify(bankClient, times(1)).processPayment(any());
    }

    @Test
    @DisplayName("Should create separate payments for different idempotency keys")
    void shouldCreateSeparatePayments_whenDifferentIdempotencyKeys() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());

      // when
      MvcResult firstResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .header("Idempotency-Key", UUID.randomUUID().toString())
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      MvcResult secondResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .header("Idempotency-Key", UUID.randomUUID().toString())
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      // then
      String firstId = objectMapper
          .readTree(firstResult.getResponse().getContentAsString()).get("id").asText();
      String secondId = objectMapper
          .readTree(secondResult.getResponse().getContentAsString()).get("id").asText();

      assertNotEquals(firstId, secondId);
      verify(bankClient, times(2)).processPayment(any());
    }

    @Test
    @DisplayName("Should create new payment each time when no idempotency key is provided")
    void shouldCreateNewPayment_whenNoIdempotencyKey() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());

      // when
      MvcResult firstResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      MvcResult secondResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      // then
      String firstId = objectMapper
          .readTree(firstResult.getResponse().getContentAsString()).get("id").asText();
      String secondId = objectMapper
          .readTree(secondResult.getResponse().getContentAsString()).get("id").asText();

      assertNotEquals(firstId, secondId);
    }

    @Test
    @DisplayName("Should return original response when same idempotency key is sent with different body")
    void shouldReturnOriginalResponse_whenSameKeyWithDifferentBody() throws Exception {
      // given
      when(bankClient.processPayment(any())).thenReturn(authorizedBankResponse());
      String idempotencyKey = UUID.randomUUID().toString();

      // when — first request
      MvcResult firstResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .header("Idempotency-Key", idempotencyKey)
              .content(validPaymentJson()))
          .andExpect(status().isOk())
          .andReturn();

      // when — second request with different body but same key
      String differentBodyJson = """
          {
            "card_number": "2222405343248877",
            "expiry_month": 4,
            "expiry_year": 2026,
            "currency": "USD",
            "amount": 999,
            "cvv": "456"
          }
          """;

      MvcResult secondResult = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .header("Idempotency-Key", idempotencyKey)
              .content(differentBodyJson))
          .andExpect(status().isOk())
          .andReturn();

      // then
      assertEquals(firstResult.getResponse().getContentAsString(),
          secondResult.getResponse().getContentAsString());
      verify(bankClient, times(1)).processPayment(any());
    }
  }
}
