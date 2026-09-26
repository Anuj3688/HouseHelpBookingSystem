# Database Core Entities Specification

This document reflects the entities currently implemented under `com.househelper.model` and persisted with Spring Data JPA in the H2 database.

## Entity relationships

```text
Customer (1) ──< Booking (N)
Helper   (1) ──< HelperAvailability (N)
Booking      ──> Payment records (linked by booking ID)
Booking / Helper workflows ──> SystemEvent audit records
```

Bookings reference a customer through a required JPA `ManyToOne` relationship. The assigned helper is stored as `assignedHelperId`. Availability references a helper through `ManyToOne`. Payment and audit event associations are represented by IDs/aggregate fields rather than JPA entity relationships.

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
| `assignedHelperId` | `Long` | Required helper ID. |
| `locality` | `String` | Required, non-blank. |
| `skill` | `SkillType` | Required enum string. |
| `bookingDate` | `LocalDate` | Required. |
| `startTime`, `endTime` | `LocalTime` | Required. |
| `totalAmount` | `Double` | Required; non-negative. |
| `status` | `BookingStatus` | Required enum string. |
| `version` | `Long` | JPA `@Version` optimistic locking field. |

`BookingStatus` values: `CONFIRMED`, `CANCELLED`, `RESCHEDULED`.

## `Payment`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `bookingId` | `Long` | Required booking ID. |
| `amount` | `Double` | Required; non-negative. |
| `paymentMethod` | `String` | Required. |
| `paymentStatus` | `PaymentStatus` | Required enum string. |
| `version` | `Long` | JPA `@Version` field. |

`PaymentStatus` values: `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`. The mock payment resource allows a pending payment to transition to `SUCCESS` or `FAILED`. Refund status is assigned by booking cancellation or rescheduling logic. These are database state transitions only; no real provider is connected.

## `SystemEvent`

| Field | Type | Persistence / validation |
| --- | --- | --- |
| `id` | `Long` | Generated identity primary key. |
| `eventType` | `String` | Required event name. |
| `aggregateType` | `String` | Required aggregate name. |
| `aggregateId` | `String` | Required aggregate identifier. |
| `helperId`, `customerId`, `paymentId`, `bookingId` | `Long` | Nullable searchable identifiers for filtering events by related records. |
| `payload` | `String` | Required JSON snapshot stored as a large object. |
| `createdAt` | `Instant` | Set at persistence time and immutable; exposed with a human-readable UTC time as well. |

`EventPublisherService` writes audit snapshots in the active transaction for booking creation, rescheduling, cancellation, payment updates, and helper availability updates. `GET /api/housekeeping/events` can filter by any combination of the four nullable identifiers.
