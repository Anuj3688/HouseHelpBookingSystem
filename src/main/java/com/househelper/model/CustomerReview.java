package com.househelper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_reviews", indexes = {
        @Index(name = "idx_review_customer_created", columnList = "customer_id, created_at"),
        @Index(name = "idx_review_helper_id", columnList = "helper_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_review_booking", columnNames = {"booking_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Customer customer;

    @NotNull
    @Column(name = "helper_id", nullable = false)
    private UUID helperId;

    @NotNull
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "review", length = 1000)
    private String review;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
