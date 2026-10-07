# HoseHelpBookingSystem API Contracts

This document describes the REST API currently implemented by the Spring Boot application. The API uses JSON request and response bodies unless noted otherwise.

## Base URL

```text
http://localhost:8080
```

Swagger UI is available at `/swagger-ui/index.html`; the root path `/` redirects there. OpenAPI JSON is available at `/v3/api-docs`.

## Customers

Customer IDs and all other entity identifiers are generated UUIDs, represented as UUID strings in JSON and URL path/query parameters. A booking must reference an existing customer UUID.

### Create a customer

`POST /api/customers` — returns `201 Created`.

```json
{
  \"name\": \"Asha Sharma\",
  \"address\": \"12 Example Road, Bengaluru\",
  \"phone\": \"9876543210\",
  \"email\": \"asha.sharma@example.com\"
}
```

The response includes the generated `id`, `name`, `address`, `phone`, `email`, `rating`, and `ratingCount`.

### List customers

`GET /api/customers`

Returns all registered customers. The housekeeping endpoint `GET /api/housekeeping/customers` provides the same customer list for test/setup workflows.

### Get, update, and delete a customer

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/api/customers/{customerId}` | Returns one customer, or `404` if not found. |
| `GET` | `/api/customers/{customerId}/bookings` | Returns all of the customer's bookings, ordered by date and start time, including status and `seriesId` for recurring occurrences. |
| `PUT` | `/api/customers/{customerId}` | Replaces the customer's name, address, phone, and email. |
| `DELETE` | `/api/customers/{customerId}` | Deletes a customer with no booking history; returns `204`. A customer with any booking history cannot be deleted and receives `409`. |

### Customer Reviews & Maid Feedback

After a booking is completed (`COMPLETED` status), the helper can submit a rating and review for the customer.

| Method | Path | Request / Behavior |
| --- | --- | --- |
| `POST` | `/api/customers/{customerId}/reviews` | Body: `{"bookingId":"...","helperId":"...","rating":5,"review":"Very polite customer"}`. Rating must be 1–5. Automatically updates customer average rating under concurrency lock. |
| `GET` | `/api/customers/{customerId}/reviews` | Returns all reviews submitted by helpers for this customer. |

## Helpers

### Onboard a helper

`POST /api/helpers`

```json
{
  "name": "Sunita Devi",
  "phone": "5550101",
  "gender": "FEMALE",
  "localities": ["Koramangala", "HSR Layout"],
  "skills": ["CLEANING", "COOKING"],
  "hourlyRate": 350.0,
  "governmentIdProof": "identity-proof-value"
}
```

Gender must be `FEMALE`, `MALE`, or `OTHER`. Localities and skills must be non-empty; at most three localities are allowed. The phone number must be unique. Government ID proof is encrypted at rest using AES-GCM and the configured encryption key.

### Submit a helper rating

`POST /api/helpers/{helperId}/ratings`

```json
{
  "rating": 5
}
```

The rating must be a whole number from 1 to 5. Each request adds one rating. The helper stores the cumulative rating total and rating count; the average is calculated from these values and returned with the updated count.

### Set helper availability

`PUT /api/helpers/{helperId}/availability`

The body is an array of slots. Each slot includes a date, start/end times, and status (`AVAILABLE`, `BOOKED`, or `NOT_AVAILABLE`).

```json
[
  {
    "slotDate": "2026-09-30",
    "startTime": "09:00:00",
    "endTime": "10:00:00",
    "status": "AVAILABLE"
  }
]
```

Availability uses fixed one-hour slots starting on the hour (for example, `09:00`–`10:00` or `10:00`–`11:00`). Other durations and fractional-hour boundaries are rejected. The response gives the helper ID and count of updated slots.

### Search available helpers

`GET /api/helpers?locality=Koramangala&skill=CLEANING&date=2026-09-30&startTime=09:00:00&endTime=10:00:00&gender=FEMALE&maxHourlyRate=400&minRating=3&page=0&size=20`

Locality, skill, date, start time, and end time are required. Continuous multi-hour requests (e.g. `09:00` to `12:00`) check all consecutive 1-hour slots. Composable filter criteria support optional filters: `gender`, `maxHourlyRate`, and `minRating` (0–5). Results are ordered by hourly rate ascending, then rating descending.

### Helper Emergency Cancellation

Allows helpers to report emergency leave or cancel a specific booking without penalty.

| Method | Path | Request / Behavior |
| --- | --- | --- |
| `POST` | `/api/helpers/{helperId}/bookings/{bookingId}/cancel` | Returns `202 Accepted`. Body: `{"reason":"Medical emergency"}`. Cancels helper assignment, sets slot to `NOT_AVAILABLE`, transitions booking to `PENDING_REASSIGNMENT`, and enqueues task into the reassignment queue. |
| `POST` | `/api/helpers/{helperId}/emergency-cancel` | Returns `202 Accepted`. Body: `{"fromDate":"2026-10-07","toDate":"2026-10-10","reason":"Illness"}`. Cancels all active future bookings for helper in range, sets availability slots to `NOT_AVAILABLE`, sets bookings to `PENDING_REASSIGNMENT`, and queues tasks. |

## Reassignment Queue Management

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/api/reassignments/tasks` | Lists all emergency reassignment tasks, their statuses (`PENDING`, `REASSIGNED`, `FAILED_CANCELLED`), and assigned substitute helper IDs. |
| `POST` | `/api/reassignments/process` | Synchronously checks and processes all pending queue tasks, reallocating substitute helpers or executing fallback auto-cancellations with 100% refund. |

## Bookings

Consolidated under `BookingResource` (`/api/bookings`).

### Create a booking

`POST /api/bookings`

Supports polymorphic booking types: `SCHEDULED`, `INSTANT`, and `RECURRING`.

#### Scheduled Booking Payload:
```json
{
  "bookingType": "SCHEDULED",
  "customerId": "550e8400-e29b-41d4-a716-446655440001",
  "locality": "Koramangala",
  "skill": "CLEANING",
  "bookingDate": "2026-09-30",
  "startTime": "09:00:00",
  "endTime": "11:00:00",
  "paymentMethod": "CARD"
}
```

#### Instant Booking Payload:
```json
{
  "bookingType": "INSTANT",
  "customerId": "550e8400-e29b-41d4-a716-446655440001",
  "locality": "Koramangala",
  "skill": "CLEANING",
  "paymentMethod": "CARD"
}
```

#### Recurring Series Payload:
```json
{
  "bookingType": "RECURRING",
  "customerId": "550e8400-e29b-41d4-a716-446655440001",
  "locality": "Koramangala",
  "skill": "CLEANING",
  "bookingDate": "2026-09-30",
  "startTime": "09:00:00",
  "endTime": "10:00:00",
  "occurrenceCount": 4,
  "recurrenceDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "paymentMethod": "CARD"
}
```

### Complete a booking

`POST /api/bookings/{bookingId}/complete`

Marks a confirmed booking as `COMPLETED`. Required before a helper can submit a customer review.

### Reschedule or cancel a booking

| Method | Path | Request / behavior |
| --- | --- | --- |
| `PUT` | `/api/bookings/{bookingId}/reschedule` | Body: `{"newBookingDate":"2026-10-01","newStartTime":"10:00:00","newEndTime":"11:00:00"}`. Finds and reserves available helper slots, releases old slots, and updates booking. |
| `POST` | `/api/bookings/{bookingId}/cancel` | Customer-initiated cancel. Releases current slots, creates at most one pending `CANCEL_REFUND` record, and writes an audit event. |
| `POST` | `/api/bookings/series/{seriesId}/cancel` | Cancels all remaining active occurrences of a recurring series and creates a consolidated refund request. |

## Mock Payments

| Method | Path | Request / Behavior |
| --- | --- | --- |
| `GET` | `/api/payments/{paymentId}` | Retrieves payment record, type, and mock reference. |
| `PATCH` | `/api/payments/{paymentId}/status` | Body: `{"status":"SUCCESS"}` or `FAILED`. Simulates payment gateway webhook/completion. |

## Housekeeping / Observability

| Method | Path | Response |
| --- | --- | --- |
| `GET` | `/api/housekeeping/available-helpers` | Unique helpers currently having at least one `AVAILABLE` slot. |
| `GET` | `/api/housekeeping/helpers` | All registered helpers. |
| `GET` | `/api/housekeeping/customers` | All registered customers. |
| `GET` | `/api/housekeeping/available-slots` | All slots with status `AVAILABLE`. |
| `GET` | `/api/housekeeping/bookings` | All bookings with status and assigned helper. |
| `GET` | `/api/housekeeping/payments` | All payment ledger records. |
| `GET` | `/api/housekeeping/events` | Audit events log with filtering by `helperId`, `customerId`, `bookingId`, `paymentId`, `seriesId`. |
