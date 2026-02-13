# Payment Gateway

A Spring Boot API that processes card payments via a bank simulator and allows merchants to retrieve payment details.

## Requirements

- JDK 17
- Docker

## Getting Started

### Running with Docker (recommended)

```bash
docker compose up --build -d
```

This starts both the bank simulator and the payment gateway. The API will be available at **http://localhost:8090**.

### Running locally

```bash
# 1. Start the bank simulator only
docker compose up -d bank_simulator

# 2. Run the application
./gradlew bootRun
```

The gateway connects to the bank simulator at `http://localhost:8081` by default.

### Stopping

```bash
docker compose down
```

## Running Tests

Tests do not require Docker — they use mocks for all external dependencies.

```bash
./gradlew test
```

**79 tests across 4 test classes:**

| Test class | Count | What it covers |
|---|---|---|
| `PaymentRequestValidatorTest` | 35 | Validation rules for all fields, expiry date boundary cases, required field checks |
| `PaymentGatewayControllerTest` | 28 | Integration tests — full HTTP request/response cycle, security, idempotency, unknown field rejection |
| `PaymentGatewayServiceTest` | 9 | Business logic — orchestration, masking, idempotency |
| `BankClientTest` | 5 | Bank communication — success, failure, request formatting |

## CI/CD

### GitHub Actions

Every push and pull request to `main` triggers the CI pipeline (`.github/workflows/ci.yml`):
1. Checks out code
2. Sets up JDK 17
3. Builds the project
4. Runs all 79+ tests
5. Uploads the test report as an artifact

### Pre-commit Hook

A pre-commit hook runs compilation and tests before each commit, preventing broken code from entering the repository.

```bash
# Install the hook (one-time setup)
./gradlew installGitHooks
```

The hook source is tracked in `scripts/pre-commit` so all contributors get the same checks.

## API Documentation

OpenAPI/Swagger UI: **http://localhost:8090/swagger-ui/index.html**

## API Usage

All endpoints are versioned under `/api/v1`.

### Process a Payment

```bash
curl -X POST http://localhost:8090/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "card_number": "2222405343248877",
    "expiry_month": 4,
    "expiry_year": 2026,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```

**Response (200 - Authorized):**
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "Authorized",
  "card_number_last_four": "8877",
  "expiry_month": 4,
  "expiry_year": 2026,
  "currency": "GBP",
  "amount": 100
}
```

**Response (200 - Declined):** same shape with `"status": "Declined"`

**Response (400 - Validation Error):**
```json
{
  "message": "Invalid payment request: Card number must be between 14 and 19 characters"
}
```

**Response (404 - Payment Not Found):**
```json
{
  "message": "Payment not found with ID: f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

**Response (502 - Bank Unavailable):**
```json
{
  "message": "Bank is unavailable"
}
```

### Retrieve a Payment

```bash
curl http://localhost:8090/api/v1/payments/f47ac10b-58cc-4372-a567-0e02b2c3d479
```

### Health Check

```bash
curl http://localhost:8090/actuator/health
```

### Bank Simulator Rules

The bank simulator (Mountebank on port 8081) determines authorization based on the last digit of the card number:
- **Odd digit (1,3,5,7,9)**: Authorized
- **Even digit (2,4,6,8)**: Declined
- **Zero (0)**: Returns 503 (service unavailable)

## Configuration

| Property | Default | Description |
|---|---|---|
| `server.port` | `8090` | Application port |
| `BANK_SIMULATOR_URL` | `http://localhost:8081` | Bank simulator base URL |
| `management.endpoints.web.exposure.include` | `health,info` | Exposed Actuator endpoints |

When running via Docker Compose, `BANK_SIMULATOR_URL` is automatically set to `http://bank_simulator:8080` using Docker's internal DNS.

## Architecture

The application follows a layered architecture with interfaces for dependency inversion:

```
POST /api/v1/payments -> Controller -> Service -> Validator
                                           -> BankClient -> Bank Simulator (:8080)
                                           -> Repository (stores masked result)
GET /api/v1/payments/{id} -> Controller -> Service -> Repository
```

- **Controller**: Thin layer that delegates to the service. Extracts optional `Idempotency-Key` header.
- **Service** (interface + impl): Orchestrates sanitization, validation, bank communication, response mapping, and storage.
- **Validator**: Explicit validation component (not annotation-based) for full control over error messages and testability with injected `Clock`.
- **BankClient** (interface + impl): Handles HTTP communication with the bank simulator, translating errors to domain exceptions.
- **Repository**: In-memory storage using `HashMap` for payment records and `ConcurrentHashMap` for idempotency keys.
- **CommonExceptionHandler**: Global `@ControllerAdvice` that maps domain exceptions to HTTP responses using `ex.getMessage()` — the thrower controls the error message, not the handler.
- **RequestLoggingFilter**: Assigns a UUID correlation ID to each request via SLF4J MDC for log tracing.

## Key Design Decisions

- **Interface-based services**: `PaymentGatewayService` and `BankClient` are interfaces with separate implementations, following the Dependency Inversion Principle. This allows easy mocking in tests and future swapping of implementations.
- **Clock injection**: `PaymentRequestValidator` accepts a `Clock` bean, making expiry date validation fully testable without relying on system time.
- **Exception handler reuse**: All exceptions flow through the existing `CommonExceptionHandler` using `ex.getMessage()`. No handler-specific logic — the exception thrower controls the response message.
- **Idempotency**: Optional `Idempotency-Key` header prevents duplicate payment processing. The gateway caches the response and returns it for repeated requests with the same key.
- **Card masking**: Full card numbers are never stored or returned. Only the last 4 digits are persisted in `PostPaymentResponse`.
- **Explicit validator over annotations**: A dedicated `PaymentRequestValidator` component gives full control over validation logic, error aggregation (all errors returned at once), and testability.
- **Input sanitization**: Card number and CVV are trimmed of whitespace before validation to handle accidental formatting in merchant input.
- **API versioning**: All endpoints are prefixed with `/api/v1` to support future breaking changes without impacting existing merchants.
- **Strict request parsing**: Unknown JSON fields are rejected with a descriptive error, catching typos like `expiry_yea` instead of silently ignoring them.

## Assumptions

- Supported currencies are limited to USD, GBP, and EUR.
- A card expiring in the current month is still considered valid (valid until end of expiry month).
- Rejected (invalid) payments are not stored in the repository.
- Card numbers are 14-19 digits (numeric only).
- CVV is 3-4 digits (string type to preserve leading zeros).

## Observability (SRE Golden Signals)

The logging strategy is built around Google's SRE **Four Golden Signals**: latency, traffic, errors, and saturation.

### System Metrics (RequestLoggingFilter)

Every HTTP request is logged with:
- **Method + path** (traffic) — what's being called and how often
- **Status code** (errors) — 4xx/5xx rates filterable in log aggregators
- **Duration in ms** (latency) — per-request timing for p50/p95/p99 analysis
- **Correlation ID** — traces a single request across all log lines

### Business Metrics (PaymentGatewayServiceImpl)

Structured log events at each processing stage:

| Event | Level | Fields | What it measures |
|---|---|---|---|
| `payment.received` | INFO | amount, currency | Inbound traffic by currency |
| `payment.validation_failed` | WARN | errorCount, errors | Rejection rate and common errors |
| `payment.bank_responded` | INFO | authorized, bankLatencyMs | Bank latency and authorization rate |
| `payment.processed` | INFO | status, amount, currency, cardLastFour | Success/decline rates by currency |
| `payment.idempotency_hit` | INFO | idempotencyKey | Cache hit rate for duplicate detection |

### Structured JSON Logging

Two log formats based on Spring profile:

- **Local development** (`!docker`) — human-readable with correlation ID:
  ```
  2026-02-09T10:15:30 [abc-123] INFO  PaymentGatewayServiceImpl - event=payment.processed status=Authorized amount=100 currency=GBP
  ```

- **Production** (`docker`) — JSON via Logstash encoder, parseable by ELK/Datadog/Splunk:
  ```json
  {"@timestamp":"2026-02-09T10:15:30","level":"INFO","message":"event=payment.processed ...","correlationId":"abc-123","paymentId":"f47ac...","httpMethod":"POST","httpPath":"/api/v1/payments","httpStatus":"200","durationMs":"45"}
  ```

### Production Extension Points

To add Prometheus metrics (not implemented, but straightforward with the existing Actuator dependency):
- `payment_processed_total{status,currency}` — counter per status/currency
- `bank_request_duration_seconds` — histogram for bank latency SLOs
- `payment_validation_errors_total` — counter for rejection monitoring
- Alert on: bank error rate > 5%, p99 latency > 2s, payment volume drop > 50%

## Security Considerations

- **Full card number never persisted or returned**: Only last 4 digits stored in response objects.
- **CVV never stored**: CVV is used only for bank communication and never appears in stored or returned data.
- **Data masking in logs and toString()**: Card numbers are masked in `toString()` and log statements. Only last 4 digits appear in logs.
- **Correlation IDs**: Each request gets a UUID correlation ID via MDC for traceability without exposing sensitive data.

## Future Enhancements

With more time, the following improvements could be made:

- **Luhn card validation**: Verify card numbers pass the Luhn algorithm check before sending to the bank.
- **Circuit breaker**: Use Resilience4j to handle bank failures gracefully with fallbacks and automatic recovery.
- **Persistent storage**: Replace in-memory `HashMap` with a database (e.g., PostgreSQL with JPA) so payments survive restarts.
- **Idempotency key expiration**: Add TTL to idempotency keys to prevent unbounded memory growth.
- **Rate limiting**: Protect against abuse with request rate limiting per merchant.
- **JWT authentication**: Secure endpoints with merchant-specific JWT tokens.
- **Metrics and monitoring**: Add Micrometer metrics for payment success/failure rates, bank latency, and error counts.
- **Retry with backoff**: Retry transient bank failures with exponential backoff before returning 502.

## Project Structure

```
src/main/java/com/checkout/payment/gateway/
  client/           BankClient interface + BankClientImpl
  configuration/    ApplicationConfiguration - RestTemplate, Clock beans
  controller/       PaymentGatewayController - REST endpoints
  enums/            PaymentStatus - AUTHORIZED, DECLINED, REJECTED
  exception/        Domain exceptions + CommonExceptionHandler
  filter/           RequestLoggingFilter - MDC correlation IDs
  model/            Request/response DTOs (PostPaymentRequest, PostPaymentResponse, BankPaymentRequest, BankPaymentResponse, ErrorResponse)
  repository/       In-memory payment + idempotency stores
  service/          PaymentGatewayService interface + PaymentGatewayServiceImpl
  validation/       PaymentRequestValidator - input validation

src/test/java/com/checkout/payment/gateway/
  client/           BankClientTest
  controller/       PaymentGatewayControllerTest (integration)
  service/          PaymentGatewayServiceTest
  validation/       PaymentRequestValidatorTest
```
