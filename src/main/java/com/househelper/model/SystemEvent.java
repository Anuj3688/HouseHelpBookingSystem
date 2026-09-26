package com.househelper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "system_events", indexes = {
        @Index(name = "idx_event_helper_id", columnList = "helper_id"),
        @Index(name = "idx_event_customer_id", columnList = "customer_id"),
        @Index(name = "idx_event_payment_id", columnList = "payment_id"),
        @Index(name = "idx_event_booking_id", columnList = "booking_id"),
        @Index(name = "idx_event_series_id", columnList = "series_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "helper_id")
    private Long helperId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "series_id")
    private Long seriesId;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    public String getHumanReadableTime() {
        return createdAt == null ? null
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                .withZone(ZoneOffset.UTC)
                .format(createdAt);
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
