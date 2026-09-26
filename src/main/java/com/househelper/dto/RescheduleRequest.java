package com.househelper.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleRequest {

    @NotNull
    private LocalDate newBookingDate;

    @NotNull
    private LocalTime newStartTime;

    @NotNull
    private LocalTime newEndTime;
}
