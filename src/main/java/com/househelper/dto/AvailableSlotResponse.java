package com.househelper.dto;

import com.househelper.model.AvailabilityStatus;
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
public class AvailableSlotResponse {

    private Long id;
    private Long helperId;
    private String helperName;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private AvailabilityStatus status;
    private Double hourlyRate;
    private Double rating;
    private Long ratingCount;
}
