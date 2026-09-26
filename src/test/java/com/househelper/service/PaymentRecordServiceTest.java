package com.househelper.service;

import com.househelper.exception.ConflictException;
import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentStatus;
import com.househelper.model.PaymentType;
import com.househelper.repository.PaymentRepository;
import com.househelper.service.payment.PaymentInitiationResult;
import com.househelper.service.payment.PaymentMethodProcessorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecordServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CancellationRefundPolicy cancellationRefundPolicy;

    @Mock
    private PaymentMethodProcessorRegistry processorRegistry;

    @InjectMocks
    private PaymentRecordService paymentRecordService;

    @Test
    @DisplayName("Creates a pending booking payment with a processor reference and series association")
    void createBookingPayment() {
        when(processorRegistry.initiate(PaymentMethod.UPI, PaymentType.BOOKING_PAYMENT, BigDecimal.valueOf(250)))
                .thenReturn(new PaymentInitiationResult("UPI-REF-1"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentRecordService.createBookingPayment(
                31L, 71L, BigDecimal.valueOf(250), PaymentMethod.UPI);

        assertEquals(31L, result.getBookingId());
        assertEquals(71L, result.getBookingSeriesId());
        assertEquals(PaymentType.BOOKING_PAYMENT, result.getPaymentType());
        assertEquals(PaymentMethod.UPI, result.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, result.getPaymentStatus());
        assertEquals("UPI-REF-1", result.getProviderReference());
    }

    @Test
    @DisplayName("Returns no adjustment and does not invoke a processor when the reschedule price is unchanged")
    void createZeroRescheduleAdjustment() {
        Payment result = paymentRecordService.createRescheduleAdjustment(31L, BigDecimal.ZERO);

        assertNull(result);
        verifyNoInteractions(paymentRepository, cancellationRefundPolicy, processorRegistry);
    }

    @Test
    @DisplayName("Creates a reschedule payment using the original booking payment method")
    void createPositiveRescheduleAdjustment() {
        Payment source = payment(101L, 31L, PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 200.0);
        when(paymentRepository.findByBookingId(31L)).thenReturn(List.of(source));
        when(processorRegistry.initiate(PaymentMethod.CARD, PaymentType.RESCHEDULE_PAYMENT, BigDecimal.valueOf(50)))
                .thenReturn(new PaymentInitiationResult("CARD-REF-2"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentRecordService.createRescheduleAdjustment(31L, 71L, BigDecimal.valueOf(50));

        assertEquals(PaymentType.RESCHEDULE_PAYMENT, result.getPaymentType());
        assertEquals(50.0, result.getAmount());
        assertEquals(PaymentMethod.CARD, result.getPaymentMethod());
        assertNull(result.getRelatedPaymentId());
        assertEquals("CARD-REF-2", result.getProviderReference());
    }

    @Test
    @DisplayName("Creates a reschedule refund linked to the original booking charge")
    void createNegativeRescheduleAdjustment() {
        Payment source = payment(101L, 31L, PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 200.0);
        when(paymentRepository.findByBookingId(31L)).thenReturn(List.of(source));
        when(processorRegistry.initiate(PaymentMethod.CARD, PaymentType.RESCHEDULE_REFUND, BigDecimal.valueOf(25)))
                .thenReturn(new PaymentInitiationResult("CARD-REF-3"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentRecordService.createRescheduleAdjustment(31L, -1L, BigDecimal.valueOf(-25));

        assertEquals(PaymentType.RESCHEDULE_REFUND, result.getPaymentType());
        assertEquals(25.0, result.getAmount());
        assertEquals(101L, result.getRelatedPaymentId());
        assertEquals(-1L, result.getBookingSeriesId());
    }

    @Test
    @DisplayName("Rejects a reschedule adjustment when no original booking charge can be found")
    void createAdjustmentWithoutSourceCharge() {
        when(paymentRepository.findByBookingId(31L)).thenReturn(List.of());

        assertThrows(ConflictException.class,
                () -> paymentRecordService.createRescheduleAdjustment(31L, BigDecimal.TEN));

        verifyNoInteractions(processorRegistry);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Creates a cancellation refund from successful charges after applying the refund policy")
    void createCancellationRefund() {
        List<Payment> payments = List.of(
                payment(101L, 31L, PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 200.0),
                payment(102L, 31L, PaymentType.RESCHEDULE_PAYMENT, PaymentStatus.SUCCESS, 50.0),
                payment(103L, 31L, PaymentType.BOOKING_PAYMENT, PaymentStatus.FAILED, 80.0),
                payment(104L, 31L, PaymentType.CANCEL_REFUND, PaymentStatus.PENDING, 20.0));
        when(paymentRepository.findByBookingId(31L)).thenReturn(payments);
        when(cancellationRefundPolicy.refundableAmount(any(), any())).thenReturn(BigDecimal.valueOf(230));
        when(processorRegistry.initiate(PaymentMethod.CARD, PaymentType.CANCEL_REFUND, BigDecimal.valueOf(230)))
                .thenReturn(new PaymentInitiationResult("REFUND-REF-4"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Payment> refund = paymentRecordService.createCancellationRefund(31L, null);

        assertEquals(230.0, refund.orElseThrow().getAmount());
        assertEquals(PaymentType.CANCEL_REFUND, refund.orElseThrow().getPaymentType());
        assertEquals(31L, refund.orElseThrow().getBookingId());
        assertNull(refund.orElseThrow().getBookingSeriesId());
        ArgumentCaptor<List<Payment>> chargesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Payment>> refundsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cancellationRefundPolicy).refundableAmount(chargesCaptor.capture(), refundsCaptor.capture());
        assertEquals(List.of(101L, 102L),
                chargesCaptor.getValue().stream().map(Payment::getId).toList());
        assertEquals(List.of(104L),
                refundsCaptor.getValue().stream().map(Payment::getId).toList());
    }

    @Test
    @DisplayName("Excludes a failed booking payment from cancellation refund eligibility")
    void excludesFailedCharge() {
        List<Payment> payments = List.of(
                payment(101L, 31L, PaymentType.BOOKING_PAYMENT, PaymentStatus.FAILED, 200.0));
        when(paymentRepository.findByBookingId(31L)).thenReturn(payments);

        Optional<Payment> refund = paymentRecordService.createCancellationRefund(31L, null);

        assertEquals(Optional.empty(), refund);
        verifyNoInteractions(cancellationRefundPolicy, processorRegistry);
    }

    @Test
    @DisplayName("Does not create a refund when the policy finds no refundable balance")
    void noRefundableBalance() {
        List<Payment> payments = List.of(
                payment(101L, 31L, PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 100.0));
        when(paymentRepository.findByBookingId(31L)).thenReturn(payments);
        when(cancellationRefundPolicy.refundableAmount(payments, List.of())).thenReturn(BigDecimal.ZERO);

        Optional<Payment> refund = paymentRecordService.createCancellationRefund(31L, null);

        assertEquals(Optional.empty(), refund);
        verifyNoInteractions(processorRegistry);
    }

    @Test
    @DisplayName("Creates one series-level refund using all payment records linked to the series")
    void createSeriesCancellationRefund() {
        List<Payment> payments = List.of(
                payment(101L, 31L, 71L, PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 100.0),
                payment(102L, 32L, 71L, PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 150.0));
        when(paymentRepository.findByBookingSeriesId(71L)).thenReturn(payments);
        when(cancellationRefundPolicy.refundableAmount(payments, List.of())).thenReturn(BigDecimal.valueOf(250));
        when(processorRegistry.initiate(PaymentMethod.CARD, PaymentType.CANCEL_REFUND, BigDecimal.valueOf(250)))
                .thenReturn(new PaymentInitiationResult("SERIES-REF-5"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment refund = paymentRecordService.createSeriesCancellationRefund(71L, List.of(31L, 32L))
                .orElseThrow();

        assertNull(refund.getBookingId());
        assertEquals(71L, refund.getBookingSeriesId());
        assertEquals(250.0, refund.getAmount());
        assertNull(refund.getRelatedPaymentId());
        verify(paymentRepository, never()).findByBookingIdIn(any());
    }

    @Test
    @DisplayName("Finds payment records associated with one booking")
    void findPaymentsForBooking() {
        List<Payment> expected = List.of(payment(101L, 31L, PaymentType.BOOKING_PAYMENT,
                PaymentStatus.PENDING, 100.0));
        when(paymentRepository.findByBookingId(31L)).thenReturn(expected);

        assertEquals(expected, paymentRecordService.findPaymentsForBooking(31L));
    }

    private Payment payment(Long id, Long bookingId, PaymentType type, PaymentStatus status, Double amount) {
        return payment(id, bookingId, null, type, status, amount);
    }

    private Payment payment(Long id, Long bookingId, Long seriesId,
                           PaymentType type, PaymentStatus status, Double amount) {
        return Payment.builder()
                .id(id)
                .bookingId(bookingId)
                .bookingSeriesId(seriesId)
                .paymentType(type)
                .paymentMethod(PaymentMethod.CARD)
                .paymentStatus(status)
                .amount(amount)
                .build();
    }
}
