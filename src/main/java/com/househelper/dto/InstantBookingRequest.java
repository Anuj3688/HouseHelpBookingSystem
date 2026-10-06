package com.househelper.dto;

import com.househelper.model.BookingType;
import com.househelper.model.PaymentMethod;
import com.househelper.model.SkillType;
import com.househelper.service.booking.BookingCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class InstantBookingRequest implements BookingCommand {

    @NotNull
    private UUID customerId;

    @NotBlank
    private String locality;

    @NotNull
    private SkillType skill;

    @NotNull
    private PaymentMethod paymentMethod;

    @Override
    public BookingType getBookingType() {
        return BookingType.INSTANT;
    }
}
