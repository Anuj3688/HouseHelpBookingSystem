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
- **Helper:** Name, unique phone, localities (maximum three), skills, hourly rate, rating, and AES-GCM encrypted government ID proof.
- **HelperAvailability:** Helper, date, start/end time, status (`AVAILABLE` or `BOOKED`), and optimistic-locking version.
- **Booking:** Required customer relationship, assigned helper ID, service locality and skill, date/time, amount, lifecycle status, and optimistic-locking version.
- **Payment:** Booking ID, amount, payment method, and status (`SUCCESS`, `PENDING`, or `REFUNDED`). No external payment provider is connected.
- **SystemEvent:** Event type, aggregate identity, JSON payload snapshot, and creation timestamp.

## Customer-aware booking

Bookings reference an existing customer through a JPA `ManyToOne` relationship. The booking request accepts the generated numeric `customerId`; a nonexistent customer is rejected.

## Housekeeping views

`GET /api/housekeeping/available-helpers`, `GET /api/housekeeping/customers`, and `GET /api/housekeeping/available-slots` expose read-only data useful for local testing.
