package com.househelper.dto;

import com.househelper.model.SkillType;
import com.househelper.model.PaymentMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class BookingSeriesRequest {

    @NotNull
    @Positive
    private UUID customerId;

    @NotBlank
    private String locality;

    @NotNull
    private SkillType skill;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    private PaymentMethod paymentMethod;

    @NotNull
    @Min(1)
    @Max(52)
    private Integer occurrenceCount;
}
