package com.househelper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "helper_availability", indexes = {
        @Index(name = "idx_availability_slot", columnList = "slot_date,start_time,status")
}, uniqueConstraints = @UniqueConstraint(name = "uk_helper_availability_start",
        columnNames = {"helper_id", "slot_date", "start_time"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelperAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "helper_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Helper helper;

    @NotNull
    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityStatus status;

    @Version
    private Long version;
}
