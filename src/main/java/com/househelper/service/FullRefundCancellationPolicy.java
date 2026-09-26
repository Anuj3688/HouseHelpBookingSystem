package com.househelper.service;

import com.househelper.model.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class FullRefundCancellationPolicy implements CancellationRefundPolicy {

    @Override
    public BigDecimal refundableAmount(List<Payment> successfulCharges, List<Payment> existingRefunds) {
        BigDecimal charged = successfulCharges.stream()
                .map(payment -> BigDecimal.valueOf(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal alreadyRefundedOrPending = existingRefunds.stream()
                .map(payment -> BigDecimal.valueOf(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return charged.subtract(alreadyRefundedOrPending).max(BigDecimal.ZERO);
    }
}
