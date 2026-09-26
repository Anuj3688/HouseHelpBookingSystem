# Maid Booking System - REST API Contracts & Architecture Specification

This document summarizes the finalized REST API contracts, database persistence strategies, security measures, and concurrency patterns designed for the Spring Boot & H2 backend machine-coding system.

---

## 1. Maid Onboarding (`POST /api/maids`)
* **Purpose:** Register a service provider with their profile, skills, hourly rate, bounded localities (1 to 3), and secure encrypted government identity verification.
* **Method & Path:** `POST /api/maids`
* **Request Payload (DTO):**
  ```json
  {
    "name": "Sunita Devi",
    "gender": "FEMALE",
    "hourlyRate": 350.00,
    "localities": ["Koramangala", "HSR Layout", "BTM Layout"],
    "skills": ["CLEANING", "COOKING"],
    "identityVerification": {
      "idType": "AADHAAR",
      "idNumber": "1234-5678-9012"
    }
  }
  ```
* **Key Design Rules:**
  * **Localities:** Mandatory, strictly between 1 and 3 items, validated against a master city locality list.
  * **Security:** `idNumber` is automatically encrypted at rest using a JPA `AttributeConverter` before saving to the H2 database.
  * **Hourly Rate:** Must be greater than `0`.

---

## 2. Update Daily Availability (`PUT /api/maids/{maidId}/availability`)
* **Purpose:** Update working hours, open slots, or block specific 1-hour time slots for a given day (e.g., leaves or schedule overrides).
* **Method & Path:** `PUT /api/maids/{maidId}/availability`
* **Request Payload (DTO):**
  ```json
  {
    "date": "2026-03-30",
    "slots": [
      {
        "startTime": "09:00:00",
        "endTime": "10:00:00",
        "status": "AVAILABLE"
      },
      {
        "startTime": "10:00:00",
        "endTime": "11:00:00",
        "status": "BLOCKED_BY_MAID"
      }
    ]
  }
  ```
* **Key Design Rules:**
  * Uses high-performance custom `@Modifying` JPQL bulk update queries to persist schedule changes efficiently and avoid N+1 query overhead.

---

## 3. Maid Search & Discovery (`GET /api/maids/search`)
* **Purpose:** Query available maids matching user criteria, returning price-range metadata, pagination, and a sorted list ordered by lowest hourly rate first.
* **Method & Path:** `GET /api/maids/search`
* **Query Parameters:**
  * `locality`: `Koramangala` (Mandatory)
  * `skill`: `CLEANING` (Mandatory)
  * `date`: `2026-03-30` (Mandatory)
  * `timeSlot`: `09:00:00` (Mandatory start time)
  * `minRating`: `4.0` (Optional)
  * `maxPrice`: `500.00` (Optional)
  * `page`: `0` (Zero-indexed pagination)
  * `size`: `10` (Page size)
* **Response Structure:** Includes price bounds (`minHourlyRate`, `maxHourlyRate`), pagination metadata, and sorted candidate profiles (`ORDER BY hourlyRate ASC`).

---

## 4. Booking Creation & Auto-Allocation (`POST /api/bookings`)
* **Purpose:** Submit an on-demand booking request. The system automatically fetches candidate maids matching the criteria, sorts them by lowest price, and attempts slot locking using optimistic locking with a 3-retry fallback loop.
* **Method & Path:** `POST /api/bookings`
* **Request Payload (DTO):**
  ```json
  {
    "customerId": "cust_9921",
    "bookingType": "INSTANT",
    "locality": "Koramangala",
    "skill": "CLEANING",
    "date": "2026-03-30",
    "startTime": "09:00:00",
    "endTime": "10:00:00",
    "paymentMethod": "UPI",
    "recurringDetails": null
  }
  ```
* **Concurrency & Safety:**
  * Uses JPA `@Version` optimistic locking on the availability slot.
  * If a concurrent transaction grabs Maid #1, the system catches the exception and automatically retries locking Maid #2 (up to 3 retries max) from the sorted candidate pool.

---

## 5. Rescheduling & Cancellation (`PUT /api/bookings/reschedule` & `POST /api/bookings/{bookingId}/cancel`)

### 5.1 Reschedule Booking
* **Method & Path:** `PUT /api/bookings/reschedule`
* **Request Payload:**
  ```json
  {
    "bookingId": 5001,
    "newDate": "2026-03-31",
    "newStartTime": "10:00:00",
    "newEndTime": "11:00:00"
  }
  ```
* **Price Delta Logic:** Calculates the price difference between the old slot and the new slot. If the new slot is more expensive, it prompts the user for a payment top-up before confirming the schedule swap.

### 5.2 Cancel Booking
* **Method & Path:** `POST /api/bookings/{bookingId}/cancel`
* **Request Payload:**
  ```json
  {
    "cancellationReason": "CUSTOMER_PLANS_CHANGED"
  }
  ```
* **Action:** Instantly flips the maid's time slot status back to `AVAILABLE` and triggers the payment refund workflow.