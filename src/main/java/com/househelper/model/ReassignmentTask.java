package com.househelper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reassignment_tasks", indexes = {
        @Index(name = "idx_reassignment_status_created", columnList = "status, created_at"),
        @Index(name = "idx_reassignment_booking_id", columnList = "booking_id"),
        @Index(name = "idx_reassignment_original_helper", columnList = "original_helper_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignmentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @NotNull
    @Column(name = "original_helper_id", nullable = false)
    private UUID originalHelperId;

    @Column(name = "reassigned_helper_id")
    private UUID reassignedHelperId;

    @Column(name = "reason", length = 1000)
    private String reason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReassignmentTaskStatus status = ReassignmentTaskStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
