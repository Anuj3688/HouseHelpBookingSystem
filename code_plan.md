# Maid Booking System: Full Code Implementation Plan and Blueprint

## 1. System Overview and Technology Stack

- **Language:** Java 17+
- **Framework:** Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Validation)
- **Database:** H2 in-memory database
- **Architecture:** Layered monolith with transactional outbox event auditing
- **Concurrency control:** Optimistic locking (`@Version`) with a three-retry loop for maid booking auto-allocation

## 2. Package Structure and Directory Layout

```text
com.example.maidbooking/
├── MaidBookingApplication.java
├── config/
│   └── AppConfig.java                 # Jackson ObjectMapper and security/encryption beans
├── converter/
│   └── EncryptedStringConverter.java  # AES/custom encryption for PII (government ID)
├── exception/
│   ├── GlobalExceptionHandler.java    # Centralized @ControllerAdvice
│   └── SlotUnavailableException.java  # Custom runtime exception for conflict handling
├── model/                             # Already specified in database_core_entities.md
│   ├── Maid.java
│   ├── MaidAvailability.java
│   ├── Booking.java
│   ├── Payment.java
│   └── SystemEvent.java
├── repository/
│   ├── MaidRepository.java
│   ├── MaidAvailabilityRepository.java
│   ├── BookingRepository.java
│   ├── PaymentRepository.java
│   └── SystemEventRepository.java
├── dto/
│   ├── MaidOnboardRequest.java
│   ├── AvailabilityRequest.java
│   ├── MaidSearchResponse.java
│   ├── BookingRequest.java
│   ├── BookingResponse.java
│   └── RescheduleRequest.java
├── service/
│   ├── MaidService.java
│   ├── BookingService.java
│   ├── PaymentService.java
│   └── EventPublisherService.java
└── controller/
    ├── MaidController.java
    └── BookingController.java
```

## 3. Core Component Specifications

### A. Configuration and Security Converters

#### `EncryptedStringConverter.java`

**Purpose:** Implements `AttributeConverter` to transparently encrypt and decrypt sensitive fields, such as `governmentIdProof`, at rest using JPA.

**Methods:**

- `convertToDatabaseColumn(String attribute)`
- `convertToEntityAttribute(String dbData)`

### B. Repositories (Data Access Layer)

#### `MaidRepository.java`

Extends `JpaRepository`.

- `Optional findByPhone(String phone)`
- `@Query Page searchMaids(String locality, SkillType skill, Pageable pageable)`

#### `MaidAvailabilityRepository.java`

Extends `JpaRepository`.

- `Optional findByMaidIdAndSlotDateAndStartTime(Long maidId, LocalDate slotDate, LocalTime startTime)`
- `@Query List findAvailableMaidsForSlot(String locality, SkillType skill, LocalDate slotDate, LocalTime startTime)`
  Results are sorted by `m.hourlyRate ASC`, then `m.rating DESC`.

#### `BookingRepository.java`

Extends `JpaRepository`.

- `List findByCustomerId(String customerId)`
- `List findByAssignedMaidId(Long assignedMaidId)`

#### `PaymentRepository.java`

Extends `JpaRepository`.

- `Optional findByBookingId(Long bookingId)`

#### `SystemEventRepository.java`

Extends `JpaRepository`.

### C. DTOs (Data Transfer Objects with Validation)

#### `MaidOnboardRequest.java`

- **Fields:** `name`, `phone`, `localities` (`Set`, maximum size 3), `skills` (`Set`), `hourlyRate`, `governmentIdProof`
- **Annotations:** `@NotBlank`, `@NotNull`, `@Size(max = 3)`

#### `AvailabilityRequest.java`

**Fields:** `slotDate`, `startTime` (`LocalTime`), `endTime` (`LocalTime`), `status` (`AvailabilityStatus`)

#### `BookingRequest.java`

**Fields:** `customerId`, `locality`, `skill`, `bookingDate`, `startTime`, `endTime`, `paymentMethod`

#### `RescheduleRequest.java`

**Fields:** `newBookingDate`, `newStartTime`, `newEndTime`

### D. Services (Core Business Logic)

#### `EventPublisherService.java`

**Responsibilities:** Persists domain event snapshots to the `system_events` table within the active transaction.

**Method:**

- `public void publishEvent(String eventType, String aggregateType, String aggregateId, Object payload)`

#### `MaidService.java`

**Responsibilities:** Handles maid onboarding, locality validation (maximum of 3), and schedule updates.

**Methods:**

- `Maid onboardMaid(MaidOnboardRequest request)`
- `void updateAvailability(Long maidId, List requests)`
- `Page searchMaids(String locality, SkillType skill, int page, int size, String sortBy)`

#### `BookingService.java`

**Responsibilities:** Implements lowest-price-first auto-allocation, a three-retry optimistic locking loop, rescheduling with price-delta calculation, and cancellations.

**Core auto-allocation algorithm:**

1. Query `MaidAvailabilityRepository.findAvailableMaidsForSlot(...)` for candidates sorted by `hourlyRate ASC`, then `rating DESC`.
2. Loop through candidates, with up to three retries for concurrency protection:
   1. Fetch the slot with `@Version`.
   2. If the status is `AVAILABLE`, update it to `BOOKED`.
   3. Catch `ObjectOptimisticLockingFailureException` and retry up to three times if another transaction claimed the slot.
3. Create and save `Booking` and `Payment` records.
4. Trigger `EventPublisherService.publishEvent("BOOKING_CREATED", ...)`.

**Methods:**

- `BookingResponse createBooking(BookingRequest request)`
- `BookingResponse rescheduleBooking(Long bookingId, RescheduleRequest request)`
- `BookingResponse cancelBooking(Long bookingId)`

### E. REST Controllers

#### `MaidController.java` (`/api/maids`)

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/onboard` | Registers a maid. |
| `POST` | `/{maidId}/availability` | Bulk-updates one-hour slots. |
| `GET` | `/search` | Paginated search by locality and skill. |

#### `BookingController.java` (`/api/bookings`)

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/` | Auto-allocates and books a maid. |
| `PUT` | `/{bookingId}/reschedule` | Reschedules an appointment and processes price-delta top-ups. |
| `POST` | `/{bookingId}/cancel` | Cancels a booking and triggers refund workflows. |

## 4. Exception Handling

### `GlobalExceptionHandler.java`

- Catches `SlotUnavailableException` or `ObjectOptimisticLockingFailureException` and maps them to HTTP `409 Conflict`.
- Catches validation failures and maps them to HTTP `400 Bad Request`.
- Catches resource-not-found errors and maps them to HTTP `404 Not Found`.