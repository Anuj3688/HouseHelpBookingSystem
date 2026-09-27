# HoseHelpBookingSystem

A Java 21 / Spring Boot REST API for discovering house-help availability and managing one-off and recurring bookings, payments, cancellations, and refunds.

##  Questions in mind while building?
1. Can a user book a maid with this app?
2. Can a user cancel a booking?
3. Can a user make payment via multiple payment methods?
4. Can a user reschedule a booking?
5. Can we track what happened in the system? (Audit trail)
6. Can we clearly see when the user made a request and which helper was assigned?

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
- **Common booking type model:** Instant, scheduled, and recurring bookings share one `Booking` model and response shape, distinguished by `BookingType`. Instant booking selects the earliest matching slot starting now or later today; scheduled booking uses the requested date/time; recurring booking expands a selected weekday pattern into individual occurrences.
- **Domain Specific Logic:** Business rules and domain logic are encapsulated within dedicated service classes, ensuring separation of concerns and maintainability.

## Query decisions, optimization & future query enhancements

### 1. Overview of Core Queries & Rationale

* **Helper Allocation Query (`findAvailableHelpersForSlot`):**
  * **Goal:** Locate candidate helpers available in a specific locality with a required skill on a given date/time slot, ordered by lowest price first.
  * **Query Strategy:** Joining `HelperAvailability` with `Helper`, filtering by `slotDate`, `startTime`, `endTime`, `status = 'AVAILABLE'`, `locality` match, and `skill` match, ordered by `helper.hourlyRate ASC`.
  * **Why:** Auto-allocates the most cost-effective helper for the customer while preserving zero double-booking through atomic state updates (`AVAILABLE -> BOOKED`).

* **Dynamic Criteria Search (`HelperSpecifications.matching`):**
  * **Goal:** Support flexible filtering (locality, skill, date, time window, max hourly rate, min average rating, gender) with pageable results.
  * **Query Strategy:** Built using JPA Criteria API (`Specification`) utilizing subqueries for element-collection set matches (`localities`, `skills`, `helper_availability`).
  * **Why:** Avoids N+1 query overhead and provides type-safe dynamic SQL generation based on optional UI filter parameters.

### 2. Current Optimization & Performance Benchmarks

* **Composite Database Indexes:**
  * `idx_availability_slot` on `helper_availability (slot_date, start_time, status)` accelerates candidate slot lookups under peak load.
  * `uk_helper_availability_start` unique constraint on `(helper_id, slot_date, start_time)` guarantees data integrity against double-slot creation.
* **Optimistic Locking & Concurrency (`@Version`):**
  * Slot status transitions rely on JPA `@Version` columns with automatic retries (`withOptimisticRetries`). This avoids heavy pessimistic database locks on read-heavy helper candidate queries.
* **Bulk Testing Results:**
  * Tested live under concurrent multi-user load with 100+ customers, 500+ helpers, and 1,000+ slots. Handled 50 concurrent booking workflows smoothly without database deadlocks.

### 3. Future Query Optimization Roadmap

1. **Geospatial Indexing (PostGIS / MySQL Spatial):**
   * *Current:* Locality matching uses string equality subqueries (`LOWER(locality)`).
   * *Future:* Replace string localities with GIS point coordinates (`ST_DWithin` / PostGIS spatial index `GIST`) to match helpers based on actual travel radius distance (e.g., within 5 km).
2. **Covering Index & Read-Replica Offloading:**
   * Move read-heavy search operations (`searchHelpers`, `getAvailableHelpers`) to dedicated database read-replicas or an Elasticsearch / OpenSearch index for ultra-low latency (< 10ms) full-text & filter queries.
3. **Redis Caching for Helper Availability:**
   * Cache open slots in Redis bitmaps or Geospatial sets to eliminate database hits for initial availability discovery. Flush/invalidate cache items on booking reservation events.

## Assumptions and current behavior

- Localities are free-form strings, not enums, because service areas can vary and grow without code releases. The frontend is assumed to trim, normalize, and deduplicate locality names case-insensitively before sending helper onboarding requests; the backend trims values but currently stores them in a case-sensitive set.
- Availability is entered as fixed one-hour slots starting on the hour. Overlapping availability is not accepted.
- Instant booking uses the server's configured local timezone and selects the earliest matching slot whose start is the current hour (when exactly on the hour) or a later hour today. If no suitable slot remains today, the request fails rather than booking a later date.
- Scheduled and recurring bookings must start in the future according to the server's configured local timezone.
- A booking reserves its slot and remains `PENDING_PAYMENT` until its simulated payment succeeds. A successful payment confirms it; a failed payment cancels the booking and releases the slot.
- Payment outcomes are set through the mock payment-status endpoint. Refund records are local pending requests; no real charge or refund is submitted to an external provider.
- Recurring series accept up to 52 occurrences and an optional set of weekdays. If weekdays are omitted, the series repeats on the start date's weekday. Available occurrences are created while unavailable dates are reported individually; every occurrence has its own payment record.
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
10. In future we can fully handle the system through wallet. Before booking we will just ask the user to add in wallet and then go with booking. Doing booking while adding money creates multiple bottelnecks and is not a good user experience. So we can have a wallet system where user can add money and then do booking. This will also help us in future to give cashback and other offers to the users.

## Questions to be answered in future?
1 . Can we authenticate if the user address is correct and validate the user profile?
2 - Can we give the Househelp an option to cancel all the future bookings?
3-  Can we give househelp prizing recommendation based on the market rate and their skillset?
4 - Can we give the user an option to add money in wallet and then do booking instead of doing booking and then adding money?
5- Can we give the user an option to extend more booking time?
6 - Currenlty booking is handled per hour basis can we make it configurable as per the type of work?
7 - Can we give the user an option to give feedback and rating to the househelp after the booking is completed?
8 - Can we add proper tracking of when househelp reached the customer and when the work was completed?
9 - Can we add Househelp feedback feature as well to handle househelp safety and security concerns?
10 - Should we give customer an option to actually choose the exact hosehelp for booking?
11- Need to add a full communication system so that user receives all necessary notifications and alerts regarding the booking, payment, and cancellation.
