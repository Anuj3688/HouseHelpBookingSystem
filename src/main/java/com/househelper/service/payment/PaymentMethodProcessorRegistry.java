package com.househelper.service.payment;

import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentMethodProcessorRegistry {

    private final Map<PaymentMethod, PaymentMethodProcessor> processors;

    public PaymentMethodProcessorRegistry(List<PaymentMethodProcessor> processors) {
        this.processors = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethodProcessor processor : processors) {
            PaymentMethodProcessor previous = this.processors.put(processor.paymentMethod(), processor);
            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple payment processors registered for " + processor.paymentMethod());
            }
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            if (!this.processors.containsKey(method)) {
                throw new IllegalStateException("No payment processor registered for " + method);
            }
        }
    }

    public PaymentInitiationResult initiate(PaymentMethod paymentMethod, PaymentType paymentType, BigDecimal amount) {
        PaymentMethodProcessor processor = processors.get(paymentMethod);
        if (processor == null) {
            throw new IllegalStateException("No payment processor registered for " + paymentMethod);
        }
        return processor.initiate(new PaymentInitiation(paymentMethod, paymentType, amount));
    }
}
