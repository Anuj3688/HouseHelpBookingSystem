# Database Core Entities Specification

This document reflects the entities currently implemented under `com.househelper.model` and persisted with Spring Data JPA in the H2 database.

## Entity relationships

```text
Customer (1) ──< Booking (N)
Customer (1) ──< BookingSeries (N) ──< Booking (N)
Helper   (1) ──< HelperAvailability (N)
Booking / BookingSeries ──> Payment records (linked by IDs)
Booking / BookingSeries / Helper workflows ──> SystemEvent audit records
```

Bookings reference a customer through a required JPA `ManyToOne` relationship and may reference a booking series. A series belongs to a customer and groups its weekly booking occurrences. The assigned helper is stored as `assignedHelperId`. Availability references a helper through `ManyToOne`. Payment and audit event associations are represented by IDs/aggregate fields rather than JPA entity relationships.

## `Customer`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `name` | `String` | Required, non-blank. |
| `address` | `String` | Required, non-blank; column length 1000. |

Customers are managed through `CustomerResource` (`/api/customers`). Deletion is rejected when the customer has booking history.

## `Helper`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `name` | `String` | Required, non-blank. |
| `phone` | `String` | Required and unique. |
| `gender` | `Gender` | Required; persisted as an enum string (`FEMALE`, `MALE`, or `OTHER`). |
| `localities` | `Set<String>` | JPA element collection; one to three non-blank localities validated by the service. |
| `skills` | `Set<SkillType>` | JPA element collection stored as enum strings. |
| `hourlyRate` | `Double` | Required and positive. |
| `totalRating` | `BigDecimal` | Required; cumulative submitted rating total, defaults to `0`. |
| `ratingCount` | `Long` | Required; number of submitted ratings, defaults to `0`. |
| `governmentIdProof` | `String` | Persisted using `EncryptedStringConverter` with AES-GCM. |

`SkillType` values: `CLEANING`, `COOKING`, `CHILD_CARE`, `ELDER_CARE`, `LAUNDRY`, `DISH_WASHING`, `OTHER`.
The helper's average rating is calculated from `totalRating / ratingCount` (or `0` when there are no ratings). `POST /api/helpers/{helperId}/ratings` adds an integer rating from 1 to 5; a pessimistic row lock prevents concurrent submissions from losing updates. Helper search requires locality, skill, and an exact available date/time slot. Gender, maximum hourly rate, and minimum rating are optional filters.

The encryption key is read from `HOUSEHELPER_ENCRYPTION_KEY`, with a development-only fallback in `application.yml`. The same key must be retained to decrypt existing values; production must use a securely managed key.

## `HelperAvailability`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `helper` | `Helper` | Required `ManyToOne`. |
| `slotDate` | `LocalDate` | Required. |
| `startTime` | `LocalTime` | Required. |
| `endTime` | `LocalTime` | Required. |
| `status` | `AvailabilityStatus` | Required enum string: `AVAILABLE` or `BOOKED`. |
| `version` | `Long` | JPA `@Version` optimistic locking field. |

The combination of helper, date, and start time is unique. Availability accepts fixed one-hour slots starting on the hour, which prevents overlap between newly submitted slots. Booking candidate queries require an exact date, start time, end time, locality, and skill match and sort helpers by hourly rate ascending, then rating descending.

## `Booking`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `customer` | `Customer` | Required `ManyToOne`, stored as `customer_id` foreign key. |
| `bookingSeries` | `BookingSeries` | Optional `ManyToOne`, stored as `booking_series_id`; null for one-off bookings. |
| `assignedHelperId` | `Long` | Required helper ID. |
| `locality` | `String` | Required, non-blank. |
| `skill` | `SkillType` | Required enum string. |
| `bookingDate` | `LocalDate` | Required. |
| `startTime`, `endTime` | `LocalTime` | Required. |
| `totalAmount` | `Double` | Required; non-negative. |
| `status` | `BookingStatus` | Required enum string. |
| `version` | `Long` | JPA `@Version` optimistic locking field. |

`BookingStatus` values: `CONFIRMED`, `CANCELLED`, `RESCHEDULED`.

## `BookingSeries`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `customer` | `Customer` | Required `ManyToOne`, stored as `customer_id`. |
| `startDate` | `LocalDate` | Required; first occurrence date. |
| `startTime`, `endTime` | `LocalTime` | Required; shared by all requested occurrences. |
| `occurrenceCount` | `Integer` | Required; API accepts 1–52 weekly occurrences. |
| `status` | `BookingSeriesStatus` | Required enum string: `ACTIVE` or `CANCELLED`. |
| `version` | `Long` | JPA `@Version` optimistic locking field. |

The `POST /api/booking-series` operation independently attempts the start date and subsequent dates spaced one week apart. Missing availability is returned as `unavailableDates`; other failures are surfaced. Cancelling an individual booking uses the standard booking cancellation route. Cancelling the series locks the series, releases all active occurrence slots, cancels those bookings, and creates at most one consolidated refund request for the successful charges in that operation.

## `Payment`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `bookingId` | `Long` | Optional booking ID; null for a series-level consolidated refund. |
| `bookingSeriesId` | `Long` | Optional series ID for tracing payments associated with a recurring occurrence or series refund. |
| `paymentType` | `PaymentType` | Required enum string: `BOOKING_PAYMENT`, `RESCHEDULE_PAYMENT`, `CANCEL_REFUND`, or `RESCHEDULE_REFUND`. |
| `relatedPaymentId` | `Long` | Optional ID of the original charge for which this refund record was created. |
| `amount` | `Double` | Required; non-negative. |
| `paymentMethod` | `String` | Required. |
| `paymentStatus` | `PaymentStatus` | Required enum string. |
| `version` | `Long` | JPA `@Version` field. |

`PaymentStatus` values: `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` (the last value is retained for compatibility with older records). Cancellation creates at most one pending refund record per cancellation operation for the remaining refundable balance, and negative reschedule adjustments create a separate pending refund record; original payment records are not overwritten. The mock payment resource allows pending charges and refund requests to transition to `SUCCESS` or `FAILED`. These are database state transitions only; no real provider is connected. Weekly booking series are modeled by `BookingSeries`; the initial full-refund behavior is implemented by the replaceable `CancellationRefundPolicy`.

## `SystemEvent`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `eventType` | `String` | Required event name. |
| `aggregateType` | `String` | Required aggregate name. |
| `aggregateId` | `String` | Required aggregate identifier. |
| `helperId`, `customerId`, `paymentId`, `bookingId`, `seriesId` | `Long` | Nullable searchable identifiers for filtering events by related records. |
| `payload` | `String` | Required JSON snapshot stored as a large object. |
| `createdAt` | `Instant` | Set at persistence time and immutable; exposed with a human-readable UTC time as well. |

`EventPublisherService` writes audit snapshots in the active transaction for booking creation, rescheduling, cancellation, payment updates, series lifecycle changes, and helper availability updates. `GET /api/housekeeping/events` can filter by any combination of the five nullable identifiers.
