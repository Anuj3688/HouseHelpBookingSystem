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
import java.util.UUID;

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

    private static UUID uuid(long value) {
        return UUID.nameUUIDFromBytes(("test-id-" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

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
        when(paymentRepository.findById(uuid(41))).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment(uuid(41));

        assertEquals(uuid(41), response.getId());
        assertEquals(uuid(17), response.getBookingId());
        assertEquals(uuid(8), response.getBookingSeriesId());
        assertEquals("MOCK-CARD-REF-41", response.getProviderReference());
        assertEquals(PaymentType.BOOKING_PAYMENT, response.getPaymentType());
        assertEquals(PaymentMethod.CARD, response.getPaymentMethod());
        assertEquals(250.0, response.getAmount());
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        verify(paymentRepository).findById(uuid(41));
        verifyNoInteractions(eventPublisherService, bookingService);
    }

    @Test
    @DisplayName("Reports a missing payment when retrieving by ID")
    void getPaymentMissing() {
        when(paymentRepository.findById(uuid(404))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getPayment(uuid(404)));

        verify(paymentRepository).findById(uuid(404));
        verifyNoInteractions(eventPublisherService, bookingService);
    }

    @Test
    @DisplayName("Marks a booking payment successful, confirms its booking, and publishes an audit event")
    void updateBookingPaymentSuccess() {
        Payment payment = payment();
        when(paymentRepository.findByIdForUpdate(uuid(41))).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(
                uuid(41), new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS));

        assertEquals(PaymentStatus.SUCCESS, response.getPaymentStatus());
        assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
        verify(bookingService).confirmBookingAfterPaymentSuccess(uuid(17), uuid(41));
        verify(bookingService, never()).cancelBookingAfterPaymentFailure(uuid(17), uuid(41));
        verify(eventPublisherService).publishEvent(
                eq("PAYMENT_STATUS_UPDATED"), eq("Payment"), eq(uuid(41).toString()),
                isNull(), isNull(), eq(uuid(41)), eq(uuid(17)), eq(uuid(8)), eq(response));
    }

    @Test
    @DisplayName("Marks a booking payment failed, cancels its booking, and publishes an audit event")
    void updateBookingPaymentFailure() {
        Payment payment = payment();
        when(paymentRepository.findByIdForUpdate(uuid(41))).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(
                uuid(41), new PaymentStatusUpdateRequest(PaymentStatus.FAILED));

        assertEquals(PaymentStatus.FAILED, response.getPaymentStatus());
        verify(bookingService).cancelBookingAfterPaymentFailure(uuid(17), uuid(41));
        verify(bookingService, never()).confirmBookingAfterPaymentSuccess(uuid(17), uuid(41));
        verify(eventPublisherService).publishEvent(
                eq("PAYMENT_STATUS_UPDATED"), eq("Payment"), eq(uuid(41).toString()),
                isNull(), isNull(), eq(uuid(41)), eq(uuid(17)), eq(uuid(8)), eq(response));
    }

    @Test
    @DisplayName("Updates refund outcomes without changing booking state")
    void updateRefundOutcome() {
        Payment payment = payment();
        payment.setPaymentType(PaymentType.CANCEL_REFUND);
        payment.setBookingId(null);
        payment.setBookingSeriesId(uuid(8));
        when(paymentRepository.findByIdForUpdate(uuid(41))).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.updatePaymentStatus(
                uuid(41), new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS));

        assertEquals(PaymentType.CANCEL_REFUND, response.getPaymentType());
        assertEquals(PaymentStatus.SUCCESS, response.getPaymentStatus());
        verifyNoInteractions(bookingService);
        verify(eventPublisherService).publishEvent(
                eq("PAYMENT_STATUS_UPDATED"), eq("Payment"), eq(uuid(41).toString()),
                isNull(), isNull(), eq(uuid(41)), isNull(), eq(uuid(8)), eq(response));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "REFUNDED"})
    @DisplayName("Rejects requested outcomes other than success or failure")
    void updatePaymentInvalidOutcome(PaymentStatus status) {
        assertThrows(InvalidRequestException.class, () -> paymentService.updatePaymentStatus(
                uuid(41), new PaymentStatusUpdateRequest(status)));

        verifyNoInteractions(paymentRepository, eventPublisherService, bookingService);
    }

    @Test
    @DisplayName("Reports a missing payment when updating its status")
    void updatePaymentMissing() {
        when(paymentRepository.findByIdForUpdate(uuid(404))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.updatePaymentStatus(
                uuid(404), new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS)));

        verify(paymentRepository).findByIdForUpdate(uuid(404));
        verifyNoInteractions(eventPublisherService, bookingService);
    }

    @Test
    @DisplayName("Rejects status updates when the payment is no longer pending")
    void updatePaymentAlreadyFinal() {
        Payment payment = payment();
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdForUpdate(uuid(41))).thenReturn(Optional.of(payment));

        assertThrows(ConflictException.class, () -> paymentService.updatePaymentStatus(
                uuid(41), new PaymentStatusUpdateRequest(PaymentStatus.FAILED)));

        verify(paymentRepository, never()).save(payment);
        verifyNoInteractions(eventPublisherService, bookingService);
    }

    private Payment payment() {
        return Payment.builder()
                .id(uuid(41))
                .bookingId(uuid(17))
                .bookingSeriesId(uuid(8))
                .providerReference("MOCK-CARD-REF-41")
                .paymentType(PaymentType.BOOKING_PAYMENT)
                .relatedPaymentId(null)
                .amount(250.0)
                .paymentMethod(PaymentMethod.CARD)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
    }
}
