package com.househelper.service.payment;

import com.househelper.model.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class UpiPaymentMethodProcessor extends AbstractMockPaymentMethodProcessor {

    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.UPI;
    }
}
