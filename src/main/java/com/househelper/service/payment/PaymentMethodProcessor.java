package com.househelper.service.payment;

import com.househelper.model.PaymentMethod;

public interface PaymentMethodProcessor {

    PaymentMethod paymentMethod();

    PaymentInitiationResult initiate(PaymentInitiation initiation);
}
