package com.househelper.service;

import com.househelper.dto.PaymentResponse;
import com.househelper.dto.PaymentStatusUpdateRequest;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentStatus;
import com.househelper.model.PaymentType;
import com.househelper.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("Returns a payment with booking, series, processor, and status details")
    void getPayment() {
        Payment payment = payment();
        when(paymentRepository.findById(41L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment(41L);

        assertEquals(41L, response.getId());
        assertEquals(17L, response.getBookingId());
        assertEquals(8L, response.getBookingSeriesId());
        assertEquals("MOCK-CARD-REF-41", response.getProviderReference());
        assertEquals(PaymentType.BOOKING_PAYMENT, response.getPaymentType());
        assertEquals(PaymentMethod.CARD, response.getPaymentMethod());
        assertEquals(250.0, response.getAmount());
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        verify(paymentRepository).findById(41L);
        verifyNoInteractions(eventPublisherService, bookingService);
    }

    @Test
    @DisplayName("Reports a missing payment when retrieving by ID")
    void getPaymentMissing() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getPayment(404L));

        verify(paymentRepository).findById(404L);
        verifyNoInteractions(eventPublisherService, bookingService);
    }

    @Test
    @DisplayName("Marks a booking payment successful, confirms its booking, and publishes an audit event")
    void updateBookingPaymentSuccess() {
        Payment payment = payment();
        when(paymentRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(
                41L, new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS));

        assertEquals(PaymentStatus.SUCCESS, response.getPaymentStatus());
        assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
        verify(bookingService).confirmBookingAfterPaymentSuccess(17L, 41L);
        verify(bookingService, never()).cancelBookingAfterPaymentFailure(17L, 41L);
        verify(eventPublisherService).publishEvent(
                eq("PAYMENT_STATUS_UPDATED"), eq("Payment"), eq("41"),
                isNull(), isNull(), eq(41L), eq(17L), eq(8L), eq(response));
    }

    @Test
    @DisplayName("Marks a booking payment failed, cancels its booking, and publishes an audit event")
    void updateBookingPaymentFailure() {
        Payment payment = payment();
        when(paymentRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(
                41L, new PaymentStatusUpdateRequest(PaymentStatus.FAILED));

        assertEquals(PaymentStatus.FAILED, response.getPaymentStatus());
        verify(bookingService).cancelBookingAfterPaymentFailure(17L, 41L);
        verify(bookingService, never()).confirmBookingAfterPaymentSuccess(17L, 41L);
        verify(eventPublisherService).publishEvent(
                eq("PAYMENT_STATUS_UPDATED"), eq("Payment"), eq("41"),
                isNull(), isNull(), eq(41L), eq(17L), eq(8L), eq(response));
    }

    @Test
    @DisplayName("Updates refund outcomes without changing booking state")
    void updateRefundOutcome() {
        Payment payment = payment();
        payment.setPaymentType(PaymentType.CANCEL_REFUND);
        payment.setBookingId(null);
        payment.setBookingSeriesId(8L);
        when(paymentRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(
                41L, new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS));

        assertEquals(PaymentType.CANCEL_REFUND, response.getPaymentType());
        assertEquals(PaymentStatus.SUCCESS, response.getPaymentStatus());
        verifyNoInteractions(bookingService);
        verify(eventPublisherService).publishEvent(
                eq("PAYMENT_STATUS_UPDATED"), eq("Payment"), eq("41"),
                isNull(), isNull(), eq(41L), isNull(), eq(8L), eq(response));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "REFUNDED"})
    @DisplayName("Rejects requested outcomes other than success or failure")
    void updatePaymentInvalidOutcome(PaymentStatus status) {
        assertThrows(InvalidRequestException.class, () -> paymentService.updatePaymentStatus(
                41L, new PaymentStatusUpdateRequest(status)));

        verifyNoInteractions(paymentRepository, eventPublisherService, bookingService);
    }

    @Test
    @DisplayName("Reports a missing payment when updating its status")
    void updatePaymentMissing() {
        when(paymentRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.updatePaymentStatus(
                404L, new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS)));

        verify(paymentRepository).findByIdForUpdate(404L);
        verifyNoInteractions(eventPublisherService, bookingService);
    }

    @Test
    @DisplayName("Rejects status updates when the payment is no longer pending")
    void updatePaymentAlreadyFinal() {
        Payment payment = payment();
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payment));

        assertThrows(ConflictException.class, () -> paymentService.updatePaymentStatus(
                41L, new PaymentStatusUpdateRequest(PaymentStatus.FAILED)));

        verify(paymentRepository, never()).save(payment);
        verifyNoInteractions(eventPublisherService, bookingService);
    }

    private Payment payment() {
        return Payment.builder()
                .id(41L)
                .bookingId(17L)
                .bookingSeriesId(8L)
                .providerReference("MOCK-CARD-REF-41")
                .paymentType(PaymentType.BOOKING_PAYMENT)
                .relatedPaymentId(null)
                .amount(250.0)
                .paymentMethod(PaymentMethod.CARD)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
    }
}
