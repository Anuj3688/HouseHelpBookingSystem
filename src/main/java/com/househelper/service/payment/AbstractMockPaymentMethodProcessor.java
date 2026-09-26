package com.househelper.service.payment;

import java.util.UUID;

abstract class AbstractMockPaymentMethodProcessor implements PaymentMethodProcessor {

    @Override
    public PaymentInitiationResult initiate(PaymentInitiation initiation) {
        return new PaymentInitiationResult("MOCK-" + paymentMethod() + "-" + UUID.randomUUID());
    }
}
