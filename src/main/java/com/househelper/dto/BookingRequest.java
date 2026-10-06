package com.househelper.dto;

import com.househelper.model.BookingType;
import com.househelper.model.SkillType;
import com.househelper.model.PaymentMethod;
import com.househelper.service.booking.BookingCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest implements BookingCommand {

    @NotNull
    private UUID customerId;

    @NotBlank
    private String locality;

    @NotNull
    private SkillType skill;

    @NotNull
    private LocalDate bookingDate;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    private PaymentMethod paymentMethod;

    @Builder.Default
    private BookingType bookingType = BookingType.SCHEDULED;

    @Override
    public BookingType getBookingType() {
        return bookingType != null ? bookingType : BookingType.SCHEDULED;
    }
}
