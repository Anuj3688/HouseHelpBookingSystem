# Database Core Entities Specification (Maid Booking System)

## Overview
This document defines the core relational database schema, entity attributes, primary/foreign keys, indices, and JPA configurations for the maid booking system. All entities are designed to run on an in-memory **H2 Database** using **Spring Data JPA**.

---

## 1. Entity Relationship Diagram (Summary)
* `Maid` (1) ──< (`MaidAvailability` (N)) [Tracks 1-hour slots per day]
* `Maid` (1) ──< (`Booking` (N)) [Linked via assigned maid ID]
* `Booking` (1) ──> (`Payment` (1)) [Tracks transaction status & payment gateway reference]
* `Booking` / `Maid` / `Payment` ──> (`SystemEvent` (N)) [Transactional outbox / audit event logs]

---

## 2. Core Entities

### A. `Maid` Entity
Stores static professional profile data, bounded localities (max 3), and encrypted identification documents.

```java
package com.example.maidbooking.model;

import com.example.maidbooking.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "maids", indexes = {
    @Index(name = "idx_maid_rating", columnList = "rating"),
    @Index(name = "idx_maid_rate", columnList = "hourlyRate")
})
@Getter
@Setter
public class Maid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long maidId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "maid_localities", joinColumns = @JoinColumn(name = "maid_id"))
    @Column(name = "locality", nullable = false)
    private Set<String> localities; // Max 3 localities validated at service layer

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "maid_skills", joinColumns = @JoinColumn(name = "maid_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false)
    private Set<SkillType> skills;

    @Column(nullable = false)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private Double rating = 5.0;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 512)
    private String governmentIdProof; // Encrypted at rest

    @CreationTimestamp
    private LocalDateTime createdAt;
}

public enum SkillType {
    CLEANING, COOKING, UTENSILS, ELDERLY_CARE
}
```

---

### B. `MaidAvailability` Entity
Tracks schedule availability on a per-day basis using fixed 1-hour time slots.

```java
package com.example.maidbooking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "maid_availability", indexes = {
    @Index(name = "idx_sched_lookup", columnList = "maidId, slotDate, startTime, status")
})
@Getter
@Setter
public class MaidAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long maidId;

    @Column(nullable = false)
    private LocalDate slotDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityStatus status = AvailabilityStatus.AVAILABLE;

    @Version
    private Long version; // Optimistic locking for concurrency control
}

public enum AvailabilityStatus {
    AVAILABLE, BOOKED, BLOCKED_BY_MAID
}
```

---

### C. `Booking` Entity
Manages customer appointments, auto-allocation states, and pricing details.

```java
package com.example.maidbooking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_customer_bookings", columnList = "customerId"),
    @Index(name = "idx_assigned_maid", columnList = "assignedMaidId")
})
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private Long assignedMaidId;

    @Column(nullable = false)
    private String locality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillType skill;

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

public enum BookingStatus {
    CONFIRMED, RESCHEDULED, CANCELLED, COMPLETED
}
```

---

### D. `Payment` Entity
Tracks transaction lifecycles, payment methods, and gateway references.

```java
package com.example.maidbooking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_booking_payment", columnList = "bookingId")
})
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Column(length = 100)
    private String gatewayTransactionId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

public enum PaymentMethod {
    UPI, CREDIT_CARD, DEBIT_CARD, NET_BANKING
}

public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, REFUNDED
}
```

---

### E. `SystemEvent` Entity (Eventing & Audit Layer)
Tracks domain milestones and serves as a local outbox log for asynchronous tracking.

```java
package com.example.maidbooking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_events", indexes = {
    @Index(name = "idx_event_type", columnList = "eventType"),
    @Index(name = "idx_aggregate", columnList = "aggregateType, aggregateId")
})
@Getter
@Setter
public class SystemEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(nullable = false, length = 100)
    private String eventType; // e.g., BOOKING_CREATED, BOOKING_CANCELLED

    @Column(nullable = false, length = 50)
    private String aggregateType; // e.g., BOOKING, MAID, PAYMENT

    @Column(nullable = false, length = 50)
    private String aggregateId; // e.g., "5001"

    @Lob
    @Column(nullable = false)
    private String payload; // JSON representation of the event snapshot

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum EventStatus {
        PENDING, PUBLISHED, FAILED
    }
}
```