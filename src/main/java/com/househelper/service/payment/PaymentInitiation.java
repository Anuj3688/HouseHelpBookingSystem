package com.househelper.service.payment;

import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentType;

import java.math.BigDecimal;

public record PaymentInitiation(
        PaymentMethod paymentMethod,
        PaymentType paymentType,
        BigDecimal amount) {
}
