package com.househelper.service;

import com.househelper.model.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullRefundCancellationPolicyTest {

    private final FullRefundCancellationPolicy policy = new FullRefundCancellationPolicy();

    @Test
    @DisplayName("Refunds the successful charge total when no refunds have been created")
    void fullRefund() {
        BigDecimal amount = policy.refundableAmount(
                List.of(payment(100.0), payment(50.25)), List.of());

        assertEquals(new BigDecimal("150.25"), amount);
    }

    @Test
    @DisplayName("Subtracts pending and successful refund records from the successful charges")
    void subtractsExistingRefunds() {
        BigDecimal amount = policy.refundableAmount(
                List.of(payment(100.0), payment(30.0)),
                List.of(payment(20.0), payment(10.0)));

        assertEquals(new BigDecimal("100.0"), amount);
    }

    @Test
    @DisplayName("Never returns a negative refund when previous refunds exceed charges")
    void clampsAtZero() {
        BigDecimal amount = policy.refundableAmount(
                List.of(payment(25.0)), List.of(payment(40.0)));

        assertEquals(BigDecimal.ZERO, amount);
    }

    private Payment payment(Double amount) {
        return Payment.builder().amount(amount).build();
    }
}
