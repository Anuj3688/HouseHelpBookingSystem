package com.househelper.service.payment;

import com.househelper.model.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class WalletPaymentMethodProcessor extends AbstractMockPaymentMethodProcessor {

    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.WALLET;
    }
}
