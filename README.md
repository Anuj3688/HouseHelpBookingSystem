# HoseHelpBookingSystem

Spring Boot API for searching, booking, and managing house-help services.

## Assumptions

- Helper availability is provided as fixed one-hour slots that start on the hour, such as `09:00`-`10:00` and `10:00`-`11:00`. Multi-hour ranges and fractional-hour slots such as `10:30`-`11:30` are not accepted.
- A helper cannot have overlapping availability. The fixed one-hour slot rule and unique helper/date/start-time key prevent overlaps for newly submitted slots.
- Booking creation currently marks the booking `CONFIRMED` and the slot `BOOKED` before the pending payment has a final outcome. If the payment fails, the application attempts to mark the booking `CANCELLED`, mark the payment `FAILED`, and release the slot in one database transaction. A database failure rolls that transaction back, leaving the booking and slot unchanged; this preserves database consistency but requires retry or operational recovery.
- If a reschedule request cannot find an available helper for the requested new slot, the API currently returns a `409 Conflict` error. The original booking and slot remain unchanged.
- Recurring bookings are created weekly for a requested number of occurrences. Available dates are booked and unavailable dates are returned individually. Customers can cancel one occurrence through the existing booking cancellation endpoint or cancel the whole series.
- Cancellation uses a replaceable `CancellationRefundPolicy`; the current policy refunds the remaining balance of successful charges in one pending refund record per cancellation operation. Cancelling a whole series releases every active occurrence's slot and creates one consolidated refund for those occurrences.

See [api_contracts_summary.md](api_contracts_summary.md) for API routes and request examples.

## Future developments

1. Evolve the booking workflow toward an event-driven architecture to coordinate payment outcomes, booking state changes, and notifications. Slot reservation must still happen atomically in the database; optimistic locking helps prevent concurrent bookings for the same slot, but does not ensure that later payment-failure cleanup will always succeed.
2. Introduce an explicit `PENDING_PAYMENT` booking state and hold the slot while payment is processed. Confirm the booking only after payment succeeds. On failure or hold expiry, cancel the booking and release the slot.
3. Improve the reschedule-unavailable response with clear guidance, such as: “No helper is available for the requested time. Your existing booking is unchanged; you can keep it or cancel it.” The API could also expose these choices in a structured response for the client.
4. Make payment-failure handling recoverable: persist a durable failure event (for example, `PaymentFailed`) using an outbox and process it through a retryable, idempotent worker. If the database is unavailable before the event can be recorded, rely on durable provider webhook redelivery or another persistent ingress queue. Retry the booking cancellation and slot release until the database reflects the outcome.
5. Notify the helper and customer about a cancellation only after the cancellation and slot release have committed successfully, so the helper is not told a job is cancelled while the system still considers it booked.
6. Integrate actual payment-provider refunds for cancellations and reschedules that reduce the booking price. The application now creates separate pending refund records (`CANCEL_REFUND` or `RESCHEDULE_REFUND`) and preserves the original charge records, but these are only local mock requests: no money is returned through a payment provider. Future work should submit and track provider refunds, handle failures with durable retries, and reconcile provider and local refund states.
7. Add reconciliation to detect mismatches between terminal payment status, booking status, and slot status, then safely retry or surface them for intervention. This protects against exhausted retries or operational failures.
8. Add a helper-initiated unavailability/cancellation flow for situations where a helper cannot work during a future period, such as next month. Identify the helper's affected bookings, attempt to reassign each booking to another suitable and available helper, and cancel any booking that cannot be reassigned. Release the original helper's affected slots, apply the relevant cancellation/refund policy, and send the customer a clear apology and update explaining whether their booking was reassigned or cancelled.
9. Consider a customer-specific wallet as an alternative to initiating a refund for eligible cancellations. Credit the refundable amount to the customer's wallet and allow it to be applied to future bookings, with auditable wallet transactions, balance protection, and clear customer choice between wallet credit and a refund to the original payment method.

This reliability work is deliberate future scope: it requires additional design around message delivery guarantees, idempotency, retries, and reconciliation, and is beyond the current mock-payment implementation.
