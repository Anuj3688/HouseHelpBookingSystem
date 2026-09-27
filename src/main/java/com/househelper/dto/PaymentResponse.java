package com.househelper.dto;

import com.househelper.model.PaymentStatus;
import com.househelper.model.PaymentType;
import com.househelper.model.PaymentMethod;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private UUID bookingId;
    private UUID bookingSeriesId;
    private String providerReference;
    private PaymentType paymentType;
    private UUID relatedPaymentId;
    private Double amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
}
