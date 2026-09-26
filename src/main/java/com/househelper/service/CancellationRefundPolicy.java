package com.househelper.service;

import com.househelper.model.Payment;

import java.math.BigDecimal;
import java.util.List;

public interface CancellationRefundPolicy {

    BigDecimal refundableAmount(List<Payment> successfulCharges, List<Payment> existingRefunds);
}
