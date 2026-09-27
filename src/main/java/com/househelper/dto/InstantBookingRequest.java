package com.househelper.dto;

import com.househelper.model.PaymentMethod;
import com.househelper.model.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class InstantBookingRequest {

    @NotNull
    private UUID customerId;

    @NotBlank
    private String locality;

    @NotNull
    private SkillType skill;

    @NotNull
    private PaymentMethod paymentMethod;
}
