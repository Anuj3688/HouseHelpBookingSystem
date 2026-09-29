# API Demo Flows

Use this guide to exercise the running API end to end. Start the application with `mvn spring-boot:run`, then send the requests to `http://localhost:8080` using Postman, curl, or another HTTP client. Swagger is available at `/swagger-ui/index.html`.

The examples use UUID placeholders such as `<CUSTOMER_ID>`. Replace each placeholder with the ID returned by the earlier request. Dates below are illustrative: choose future dates for scheduled and recurring bookings, use dates that match the selected recurrence weekdays, and use a current-date slot that starts at or after the next eligible hour for instant booking. Availability must use one-hour slots starting on the hour.

All payment processing is simulated. A payment starts in `PENDING`; use the payment-status endpoint to simulate the provider outcome. `SUCCESS` confirms a booking, while `FAILED` cancels it and releases its slot.

## 1. Create a customer

`POST /api/customers`

```json
{
  "name": "Asha Sharma",
  "address": "12 Example Road, Bengaluru"
}
```

Save the response `id` as `<CUSTOMER_ID>`.

## 2. Onboard a helper

`POST /api/helpers`

```json
{
  "name": "Sunita Devi",
  "phone": "5550101001",
  "gender": "FEMALE",
  "localities": ["Koramangala"],
  "skills": ["CLEANING", "COOKING"],
  "hourlyRate": 350.0,
  "governmentIdProof": "DEMO-GOVERNMENT-ID-001"
}
```

Use a new phone number if repeating the demo. Save the response `id` as `<HELPER_ID>`.

## 3. Add availability for all booking demonstrations

`PUT /api/helpers/<HELPER_ID>/availability`

```json
[
  {
    "slotDate": "<TODAY>",
    "startTime": "<NEXT_ELIGIBLE_HOUR>:00:00",
    "endTime": "<NEXT_ELIGIBLE_HOUR_PLUS_ONE>:00:00",
    "status": "AVAILABLE"
  },
  {
    "slotDate": "2026-10-05",
    "startTime": "09:00:00",
    "endTime": "10:00:00",
    "status": "AVAILABLE"
  },
  {
    "slotDate": "2026-10-05",
    "startTime": "10:00:00",
    "endTime": "11:00:00",
    "status": "AVAILABLE"
  },
  {
    "slotDate": "2026-10-05",
    "startTime": "13:00:00",
    "endTime": "14:00:00",
    "status": "AVAILABLE"
  },
  {
    "slotDate": "2026-10-05",
    "startTime": "15:00:00",
    "endTime": "16:00:00",
    "status": "AVAILABLE"
  },
  {
    "slotDate": "2026-10-06",
    "startTime": "09:00:00",
    "endTime": "10:00:00",
    "status": "AVAILABLE"
  },
  {
    "slotDate": "2026-10-07",
    "startTime": "09:00:00",
    "endTime": "10:00:00",
    "status": "AVAILABLE"
  },
  {
    "slotDate": "2026-10-14",
    "startTime": "09:00:00",
    "endTime": "10:00:00",
    "status": "AVAILABLE"
  },
  {
    "slotDate": "2026-10-21",
    "startTime": "09:00:00",
    "endTime": "10:00:00",
    "status": "AVAILABLE"
  }
]
```

Replace `<TODAY>` and the two instant-booking times with values appropriate to the server's local date/time. For the other dates, update the dates together if they are no longer in the future; October 5, 7, 14, and 21, 2026 are respectively a Monday and Wednesdays. The helper must serve `Koramangala` and offer the requested skill for every test to match.

## 4. Search for an available helper

`GET /api/helpers` searches available househelp using query parameters; it does not take a JSON request body. Provide the required locality, skill, date, and one-hour time range, with optional filters and pagination:

```text
locality=Koramangala
skill=CLEANING
date=2026-10-05
startTime=09:00:00
endTime=10:00:00
gender=FEMALE
maxHourlyRate=500
minRating=0
page=0
size=20
```

Example request:

`GET /api/helpers?locality=Koramangala&skill=CLEANING&date=2026-10-05&startTime=09:00:00&endTime=10:00:00&gender=FEMALE&maxHourlyRate=500&minRating=0&page=0&size=20`

The paginated response includes matching helpers in `content` and pagination metadata. Each helper entry has this shape:

```json
{
  "content": [
    {
      "id": "<HELPER_ID>",
      "name": "Sunita Devi",
      "gender": "FEMALE",
      "localities": ["Koramangala"],
      "skills": ["CLEANING", "COOKING"],
      "hourlyRate": 350.0,
      "rating": 0.0,
      "ratingCount": 0
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

Expect the helper from step 2 in `content` while the requested slot is still `AVAILABLE`. `gender`, `maxHourlyRate`, and `minRating` can be omitted; `page` defaults to `0` and `size` defaults to `20`.

## 5. Create an instant booking

`POST /api/bookings/instant`

```json
{
  "customerId": "<CUSTOMER_ID>",
  "locality": "Koramangala",
  "skill": "CLEANING",
  "paymentMethod": "CARD"
}
```

The response should identify `bookingType` as `INSTANT`, use the earliest eligible slot today, and include `paymentId`. Save those IDs as `<INSTANT_BOOKING_ID>` and `<INSTANT_PAYMENT_ID>`.

## 6. Create and pay for a scheduled booking

`POST /api/bookings`

```json
{
  "customerId": "<CUSTOMER_ID>",
  "locality": "Koramangala",
  "skill": "CLEANING",
  "bookingDate": "2026-10-05",
  "startTime": "09:00:00",
  "endTime": "10:00:00",
  "paymentMethod": "UPI"
}
```

The response should have `bookingType: "SCHEDULED"` and `status: "PENDING_PAYMENT"`. Save `<SCHEDULED_BOOKING_ID>` and `<SCHEDULED_PAYMENT_ID>`.

Get the payment:

`GET /api/payments/<SCHEDULED_PAYMENT_ID>`

Simulate a successful payment:

`PATCH /api/payments/<SCHEDULED_PAYMENT_ID>/status`

```json
{
  "status": "SUCCESS"
}
```

The booking should now be `CONFIRMED`.

## 7. Reschedule a booking

`PUT /api/bookings/<SCHEDULED_BOOKING_ID>/reschedule`

```json
{
  "newBookingDate": "2026-10-06",
  "newStartTime": "09:00:00",
  "newEndTime": "10:00:00"
}
```

This reserves the new available slot and frees the old one. It may create a `RESCHEDULE_PAYMENT` or `RESCHEDULE_REFUND` if the helper's price makes the total change; the response contains a `paymentId` only when an adjustment is created.

## 8. Cancel one booking and inspect its refund

Create a separate booking for the available October 5, 13:00 slot using `POST /api/bookings` with the same scheduled-booking payload, but set `startTime` to `13:00:00` and `endTime` to `14:00:00`. Simulate `SUCCESS` on its payment before cancelling.

`POST /api/bookings/<CANCELLABLE_BOOKING_ID>/cancel`

The response should show `CANCELLED`; the slot is released. If there was a successful charge, the cancellation creates a pending refund record. Find its ID in `GET /api/housekeeping/payments` (look for a `CANCEL_REFUND` record related to the cancelled booking), then simulate the refund outcome:

`PATCH /api/payments/<REFUND_PAYMENT_ID>/status`

```json
{
  "status": "SUCCESS"
}
```

## 9. Create a recurring series and pay each occurrence

`POST /api/booking-series`

```json
{
  "customerId": "<CUSTOMER_ID>",
  "locality": "Koramangala",
  "skill": "CLEANING",
  "startDate": "2026-10-07",
  "startTime": "09:00:00",
  "endTime": "10:00:00",
  "paymentMethod": "CARD",
  "occurrenceCount": 3,
  "recurrenceDays": ["WEDNESDAY"]
}
```

The response contains a `<SERIES_ID>` and up to three created bookings. Each occurrence has its own booking ID, payment ID, and `bookingType: "RECURRING"`. Simulate `SUCCESS` separately for each occurrence's payment before continuing if you want to demonstrate refund calculation across successful payments.

To cancel only one occurrence, call:

`POST /api/bookings/<ONE_OCCURRENCE_BOOKING_ID>/cancel`

To cancel the remaining series, call:

`POST /api/booking-series/<SERIES_ID>/cancel`

The series cancellation releases active occurrence slots and requests at most one consolidated refund for the remaining eligible successful charges.

## 10. Demonstrate a failed payment releasing its slot

Create a separate scheduled booking for the available October 5, 15:00 slot with `POST /api/bookings` (`startTime: "15:00:00"`, `endTime: "16:00:00"`), then mark its payment as failed:

`PATCH /api/payments/<FAILED_BOOKING_PAYMENT_ID>/status`

```json
{
  "status": "FAILED"
}
```

The booking becomes `CANCELLED` and its slot becomes available again.

## 11. Inspect bookings, payments, and audit events

- `GET /api/customers/<CUSTOMER_ID>/bookings` — customer's bookings, including statuses and series IDs.
- `GET /api/housekeeping/bookings` — all bookings.
- `GET /api/housekeeping/available-slots` — slots currently available.
- `GET /api/housekeeping/payments` — payment and refund records.
- `GET /api/housekeeping/events` — audit events; filter with `?customerId=<CUSTOMER_ID>`, `?bookingId=<BOOKING_ID>`, or `?seriesId=<SERIES_ID>`.

To demonstrate helper ratings:

`POST /api/helpers/<HELPER_ID>/ratings`

```json
{
  "rating": 5
}
```
