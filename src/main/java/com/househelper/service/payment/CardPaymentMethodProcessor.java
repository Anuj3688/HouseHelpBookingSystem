package com.househelper.service.payment;

import com.househelper.model.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class CardPaymentMethodProcessor extends AbstractMockPaymentMethodProcessor {

    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.CARD;
    }
}
