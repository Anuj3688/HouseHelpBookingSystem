package com.househelper.dto;

import com.househelper.model.PaymentStatus;
import com.househelper.model.PaymentType;
import com.househelper.model.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private Long bookingId;
    private Long bookingSeriesId;
    private String providerReference;
    private PaymentType paymentType;
    private Long relatedPaymentId;
    private Double amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
}
