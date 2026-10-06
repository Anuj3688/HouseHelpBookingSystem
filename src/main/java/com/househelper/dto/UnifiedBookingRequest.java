package com.househelper.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.househelper.model.BookingType;
import com.househelper.model.PaymentMethod;
import com.househelper.model.SkillType;
import com.househelper.service.booking.BookingCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * Universal polymorphic request payload supporting any booking type
 * (SCHEDULED, INSTANT, RECURRING) behind a single unified booking endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedBookingRequest implements BookingCommand {

    private BookingType bookingType;

    @NotNull
    private UUID customerId;

    @NotBlank
    private String locality;

    @NotNull
    private SkillType skill;

    @NotNull
    private PaymentMethod paymentMethod;

    // Optional for Instant; Required for Scheduled; Start date for Recurring
    @JsonAlias("startDate")
    private LocalDate bookingDate;

    // Optional for Instant; Required for Scheduled & Recurring
    private LocalTime startTime;

    // Optional for Instant; Required for Scheduled & Recurring
    private LocalTime endTime;

    // Required for Recurring
    @Min(1)
    @Max(52)
    private Integer occurrenceCount;

    // Optional for Recurring (defaults to startDate weekday if omitted)
    private Set<DayOfWeek> recurrenceDays;

    @Override
    public BookingType getBookingType() {
        if (bookingType != null) {
            return bookingType;
        }
        if (occurrenceCount != null && occurrenceCount >= 1) {
            return BookingType.RECURRING;
        }
        return BookingType.SCHEDULED;
    }

    public LocalDate getStartDate() {
        return bookingDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.bookingDate = startDate;
    }
}
