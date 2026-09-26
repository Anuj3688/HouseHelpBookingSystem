package com.househelper.service.payment;

import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentMethodProcessorRegistryTest {

    @Test
    @DisplayName("Routes card payment initiation to the registered card processor")
    void routesToProcessor() {
        PaymentMethodProcessor cardProcessor = mock(PaymentMethodProcessor.class);
        when(cardProcessor.paymentMethod()).thenReturn(PaymentMethod.CARD);
        when(cardProcessor.initiate(any(PaymentInitiation.class)))
                .thenReturn(new PaymentInitiationResult("CARD-REF"));
        PaymentMethodProcessorRegistry registry = registry(cardProcessor,
                processor(PaymentMethod.UPI), processor(PaymentMethod.WALLET));

        PaymentInitiationResult result = registry.initiate(
                PaymentMethod.CARD, PaymentType.BOOKING_PAYMENT, BigDecimal.TEN);

        assertEquals("CARD-REF", result.providerReference());
        verify(cardProcessor).initiate(
                new PaymentInitiation(PaymentMethod.CARD, PaymentType.BOOKING_PAYMENT, BigDecimal.TEN));
    }

    @Test
    @DisplayName("Rejects duplicate processors registered for the same payment method")
    void rejectsDuplicateProcessor() {
        PaymentMethodProcessor firstCardProcessor = processor(PaymentMethod.CARD);
        PaymentMethodProcessor secondCardProcessor = processor(PaymentMethod.CARD);

        assertThrows(IllegalStateException.class, () -> new PaymentMethodProcessorRegistry(
                List.of(firstCardProcessor, secondCardProcessor, processor(PaymentMethod.UPI),
                        processor(PaymentMethod.WALLET))));
    }

    @Test
    @DisplayName("Rejects startup when any supported payment method has no processor")
    void rejectsMissingProcessor() {
        assertThrows(IllegalStateException.class, () -> new PaymentMethodProcessorRegistry(
                List.of(processor(PaymentMethod.CARD), processor(PaymentMethod.UPI))));
    }

    private PaymentMethodProcessorRegistry registry(PaymentMethodProcessor... processors) {
        return new PaymentMethodProcessorRegistry(List.of(processors));
    }

    private PaymentMethodProcessor processor(PaymentMethod method) {
        PaymentMethodProcessor processor = mock(PaymentMethodProcessor.class);
        when(processor.paymentMethod()).thenReturn(method);
        return processor;
    }
}
