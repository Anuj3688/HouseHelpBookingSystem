# HoseHelpBookingSystem

A Java 21 / Spring Boot REST API for discovering house-help availability and managing one-off and recurring bookings, payments, cancellations, and refunds.

## Build and run

**Prerequisites:** Java 21 and Maven 3.8+.

```bash
# Compile, run tests, and package the executable application
mvn clean package

# Start the application in development
mvn spring-boot:run

# Or run the packaged application
java -jar target/HoseHelpBookingSystem-0.0.1-SNAPSHOT.jar
```

The API runs at `http://localhost:8080`. Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`; the API contract is at `http://localhost:8080/v3/api-docs`. H2 is configured as an in-memory database, so its contents do not persist across application restarts. The development H2 console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:househelperdb`, user `sa`, blank password).

For production, configure `HOUSEHELPER_ENCRYPTION_KEY` with a securely managed, Base64-encoded 256-bit AES key. The fallback key in `application.yml` is for local development only; changing the key prevents decrypting data encrypted with the previous key.

## Design and key decisions

- **Layered architecture:** REST resources handle HTTP, DTOs define request/response contracts, services implement workflows, repositories encapsulate persistence, and JPA entities represent stored data.
- **Strategy pattern for payments:** `PaymentMethodProcessor` provides a shared abstraction for CARD, UPI, and WALLET, with a registry selecting the matching processor. Current processors are mocks: they generate local references and do not move money or maintain a wallet balance.
- **Policy pattern for cancellation refunds:** `CancellationRefundPolicy` separates refund calculation from cancellation orchestration. The current full-refund policy returns the remaining refundable balance and can be replaced with another policy.
- **Optimistic locking and transactional booking operations:** Availability and booking-series records use JPA `@Version`; booking allocation, rescheduling, cancellation, and slot release are coordinated transactionally to protect state changes from concurrent updates.
- **UUID identifiers:** Entity IDs and related API identifiers use UUIDs, avoiding predictable sequential IDs.
- **Audit trail and application logs:** Lifecycle changes are recorded as `SystemEvent` snapshots in the database and can be inspected through `GET /api/housekeeping/events`. Application diagnostics use SLF4J; SQL logging is enabled for local development and should be reviewed/disabled appropriately for production.
- **Recurring bookings and payments:** A weekly series creates each available occurrence independently; every occurrence has its own booking and payment. A whole-series cancellation releases active occurrence slots and requests one consolidated refund record.

## Assumptions and current behavior

- Availability is entered as fixed one-hour slots starting on the hour. Overlapping availability is not accepted.
- A booking reserves its slot and remains `PENDING_PAYMENT` until its simulated payment succeeds. A successful payment confirms it; a failed payment cancels the booking and releases the slot.
- Payment outcomes are set through the mock payment-status endpoint. Refund records are local pending requests; no real charge or refund is submitted to an external provider.
- Weekly series accept a requested occurrence count. Available occurrences are created while unavailable dates are reported individually.
- A cancellation releases the relevant slot(s) and applies the configured refund policy. Single-occurrence cancellation and whole-series cancellation are separate operations.
- If rescheduling cannot find an available helper for the requested time, the request fails with `409 Conflict` and the existing booking remains unchanged.

See [api_contracts_summary.md](api_contracts_summary.md) for routes, payloads, and detailed API behavior. See [database_core_entities.md](database_core_entities.md) and [database_core_entities_specification.md](database_core_entities_specification.md) for the persistence model.

## With more time

1. Evolve the booking workflow toward an event-driven architecture to coordinate payment outcomes, booking state changes, and notifications. Slot reservation must remain atomic in the database; optimistic locking protects concurrent slot updates, but does not guarantee later payment-failure cleanup will succeed.
2. Connect CARD, UPI, and WALLET processor interfaces to real payment providers. Add verified provider webhooks, idempotency keys, payment timeouts, and secure credentials/payment-data handling; current processors only create mock references and pending local records.
3. Improve the reschedule-unavailable response with clear guidance that the existing booking is unchanged and the customer can keep or cancel it. Expose these choices in a structured response for client applications.
4. Make payment-failure handling recoverable: persist durable failure events (for example, with an outbox) and process them through a retryable, idempotent worker. If the database is unavailable before recording an event, rely on durable provider webhook redelivery or another persistent ingress queue, and retry cancellation and slot release until the database reflects the outcome.
5. Notify the helper and customer about a cancellation only after the cancellation and slot release have committed successfully.
6. Integrate actual provider refunds for cancellations and reschedules that reduce the booking price. The current `CANCEL_REFUND` and `RESCHEDULE_REFUND` records are local mock requests and do not return money; future work should submit and track provider refunds, retry failures durably, and reconcile provider and local states.
7. Add reconciliation to detect mismatches between terminal payment status, booking status, and slot status, then safely retry or surface them for intervention.
8. Add helper-initiated unavailability management for future periods. Find affected upcoming bookings, reassign each to another suitable and available helper where possible, and otherwise cancel it, release the original slot, apply the refund policy, and send the customer a clear apology and update.
9. Add an optional customer-specific wallet as an alternative to provider refunds. Credit eligible refundable amounts and allow them to be applied to future bookings, with auditable wallet transactions, concurrency-safe balances, and a clear customer choice between wallet credit and a refund to the original payment method.
