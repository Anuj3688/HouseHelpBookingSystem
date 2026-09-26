# HoseHelpBookingSystem

Spring Boot API for searching, booking, and managing house-help services.

## Assumptions

- Helper availability is provided as fixed one-hour slots that start on the hour, such as `09:00`-`10:00` and `10:00`-`11:00`. Multi-hour ranges and fractional-hour slots such as `10:30`-`11:30` are not accepted.
- A helper cannot have overlapping availability. The fixed one-hour slot rule and unique helper/date/start-time key prevent overlaps for newly submitted slots.
- Booking creation currently marks the booking `CONFIRMED` and the slot `BOOKED` before the pending payment has a final outcome. If the payment fails, the application attempts to mark the booking `CANCELLED`, mark the payment `FAILED`, and release the slot in one database transaction. A database failure rolls that transaction back, leaving the booking and slot unchanged; this preserves database consistency but requires retry or operational recovery.

See [api_contracts_summary.md](api_contracts_summary.md) for API routes and request examples.

## Future developments

- The booking workflow could evolve toward an event-driven architecture to coordinate payment outcomes, booking state changes, and notifications. Slot reservation must still happen atomically in the database; optimistic locking helps prevent concurrent bookings for the same slot, but does not ensure that later payment-failure cleanup will always succeed.
- Introduce an explicit `PENDING_PAYMENT` booking state and hold the slot while payment is processed. Confirm the booking only after payment succeeds. On failure or hold expiry, cancel the booking and release the slot.
- Make payment-failure handling recoverable: persist a durable failure event (for example, `PaymentFailed`) using an outbox and process it through a retryable, idempotent worker. If the database is unavailable before the event can be recorded, rely on durable provider webhook redelivery or another persistent ingress queue. Retry the booking cancellation and slot release until the database reflects the outcome.
- Notify the helper and customer about a cancellation only after the cancellation and slot release have committed successfully, so the helper is not told a job is cancelled while the system still considers it booked.
- Add reconciliation to detect mismatches between terminal payment status, booking status, and slot status, then safely retry or surface them for intervention. This protects against exhausted retries or operational failures.
- This reliability work is a deliberate future development: it requires additional design around message delivery guarantees, idempotency, retries, and reconciliation, and is beyond the scope of the current mock-payment implementation.
