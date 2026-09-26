# HoseHelpBookingSystem: Implemented Backend Overview

This document summarizes the current implementation. API request/response details are in `api_contracts_summary.md`; entity field details are in `database_core_entities_specification.md`.

## Technology

- Java 21
- Spring Boot 3.2.5
- Spring Web MVC, Spring Data JPA, Jakarta Validation
- H2 in-memory database
- Lombok
- Springdoc OpenAPI / Swagger UI

## Package layout

```text
com.househelper/
├── HoseHelpBookingSystemApplication.java
├── converter/
│   └── EncryptedStringConverter.java
├── dto/
│   ├── AvailableSlotResponse.java
│   ├── AvailabilityRequest.java
│   ├── BookingRequest.java
│   ├── BookingResponse.java
│   ├── CustomerRequest.java
│   ├── CustomerResponse.java
│   ├── HelperOnboardRequest.java
│   ├── HelperSearchResponse.java
│   └── RescheduleRequest.java
├── exception/
│   ├── ConflictException.java
│   ├── GlobalExceptionHandler.java
│   ├── InvalidRequestException.java
│   ├── ResourceNotFoundException.java
│   └── SlotUnavailableException.java
├── model/
│   ├── AvailabilityStatus.java
│   ├── Booking.java
│   ├── BookingStatus.java
│   ├── Customer.java
│   ├── Helper.java
│   ├── HelperAvailability.java
│   ├── Payment.java
│   ├── PaymentStatus.java
│   ├── SkillType.java
│   └── SystemEvent.java
├── repository/
│   ├── BookingRepository.java
│   ├── CustomerRepository.java
│   ├── HelperAvailabilityRepository.java
│   ├── HelperRepository.java
│   ├── PaymentRepository.java
│   └── SystemEventRepository.java
├── resources/
│   ├── BookingResource.java
│   ├── CustomerResource.java
│   ├── HelperResource.java
│   ├── HousekeepingResource.java
│   └── HomeResource.java
└── service/
    ├── BookingService.java
    ├── CustomerService.java
    ├── EventPublisherService.java
    ├── HelperService.java
    └── HousekeepingService.java
```

## Implemented behavior

### Customer management

- Create, list, get, update, and delete customers under `/api/customers`.
- Customer IDs are generated numeric IDs.
- Booking creation requires a valid customer record.
- Deleting a customer with booking history returns a conflict.

### Helper and availability management

- Onboard helpers with a unique phone number, gender, up to three localities, skills, hourly rate, and encrypted government ID proof.
- Add or update availability slots; invalid time ranges and overlaps are rejected.
- Search available helpers by locality, skill, exact date/time slot, and optional gender, price, and rating filters; results are ordered by lowest hourly rate and then highest rating.

### Booking lifecycle

- Allocate an available helper for an exact slot, ordered by rate and rating.
- Use optimistic locking and retry up to three times for concurrent modifications.
- Create a booking and a pending payment record in one transaction.
- Reschedule by reserving a new slot, releasing the old slot, updating the booking, and recording the price difference.
- Cancel by releasing the slot, marking payments refunded, and updating the booking.
- Simulate payment outcomes by transitioning pending payments to `SUCCESS` or `FAILED` through the payment resource.
- Persist booking lifecycle audit events to `SystemEvent`.

Payment/refund behavior is database bookkeeping only; no payment gateway or external refund workflow is integrated.

### Housekeeping endpoints

- `GET /api/housekeeping/available-helpers` lists helpers with at least one slot marked `AVAILABLE`.
- `GET /api/housekeeping/customers` lists registered customers.
- `GET /api/housekeeping/available-slots` lists slots marked `AVAILABLE`.
- `GET /api/payments/{paymentId}` retrieves a payment; `PATCH /api/payments/{paymentId}/status` simulates a pending payment outcome.

These endpoints support local API exploration and test-data setup. Available listings are not filtered by a requested date.

### Errors, logging, and API docs

- `GlobalExceptionHandler` maps validation errors to 400, missing records/resources to 404, conflicts to 409, and unexpected failures to 500.
- Critical failures are logged; expected client errors and routine success paths are not logged at info level.
- `/` redirects to Swagger UI at `/swagger-ui/index.html`; the OpenAPI document is at `/v3/api-docs`.
- Missing static resources such as `/favicon.ico` return 404 rather than being reported as unexpected server errors.
