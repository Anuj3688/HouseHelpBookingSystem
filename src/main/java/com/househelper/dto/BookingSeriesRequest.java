package com.househelper.dto;

import com.househelper.model.BookingType;
import com.househelper.model.PaymentMethod;
import com.househelper.model.SkillType;
import com.househelper.service.booking.BookingCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.Set;
import java.util.UUID;

@Data
public class BookingSeriesRequest implements BookingCommand {

    @NotNull
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

    private Set<DayOfWeek> recurrenceDays;

    @Override
    public BookingType getBookingType() {
        return BookingType.RECURRING;
    }
}
