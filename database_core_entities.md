# Database Core Entities

This is the concise entity and relationship overview for the implemented application. Refer to [`database_core_entities_specification.md`](database_core_entities_specification.md) for fields, constraints, and enum values.

```text
Customer (1) ──< Booking (N)
Helper   (1) ──< HelperAvailability (N)
Booking      ──> Payment records (linked by booking ID)
Booking lifecycle ──> SystemEvent audit snapshots
```

## Entities

- **Customer:** Generated numeric ID, name, and address. Customers with booking history cannot be deleted.
- **Helper:** Name, unique phone, gender (`FEMALE`, `MALE`, or `OTHER`), localities (maximum three), skills, hourly rate, cumulative rating total and count (average calculated on read), and AES-GCM encrypted government ID proof.
- **HelperAvailability:** Helper, date, start/end time, status (`AVAILABLE` or `BOOKED`), and optimistic-locking version.
- **BookingSeries:** Customer, weekly start date and time, requested occurrence count, lifecycle status, and optimistic-locking version.
- **Booking:** Required customer relationship, optional booking-series relationship, assigned helper ID, service locality and skill, date/time, amount, lifecycle status, and optimistic-locking version.
- **Payment:** Optional booking and booking-series IDs, type (`BOOKING_PAYMENT`, `RESCHEDULE_PAYMENT`, `CANCEL_REFUND`, or `RESCHEDULE_REFUND`), optional related source-payment ID, amount, payment method, and status. Cancellations create at most one consolidated pending refund record per cancellation operation; original charge records are preserved. No external payment provider is connected.
- **SystemEvent:** Event type, aggregate identity, nullable helper/customer/payment/booking/series identifiers, JSON payload snapshot, and creation timestamp. Booking lifecycle, series lifecycle, and helper availability updates are audited.

## Customer-aware booking

Bookings reference an existing customer through a JPA `ManyToOne` relationship. The booking request accepts the generated numeric `customerId`; a nonexistent customer is rejected.

## Housekeeping views

`GET /api/housekeeping/available-helpers`, `GET /api/housekeeping/customers`, and `GET /api/housekeeping/available-slots` expose read-only data useful for local testing.
