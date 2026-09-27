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
import java.util.UUID;

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

    private static UUID uuid(long value) {
        return UUID.nameUUIDFromBytes(("test-id-" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

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
                uuid(31), uuid(71), BigDecimal.valueOf(250), PaymentMethod.UPI);

        assertEquals(uuid(31), result.getBookingId());
        assertEquals(uuid(71), result.getBookingSeriesId());
        assertEquals(PaymentType.BOOKING_PAYMENT, result.getPaymentType());
        assertEquals(PaymentMethod.UPI, result.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, result.getPaymentStatus());
        assertEquals("UPI-REF-1", result.getProviderReference());
    }

    @Test
    @DisplayName("Returns no adjustment and does not invoke a processor when the reschedule price is unchanged")
    void createZeroRescheduleAdjustment() {
        Payment result = paymentRecordService.createRescheduleAdjustment(uuid(31), BigDecimal.ZERO);

        assertNull(result);
        verifyNoInteractions(paymentRepository, cancellationRefundPolicy, processorRegistry);
    }

    @Test
    @DisplayName("Creates a reschedule payment using the original booking payment method")
    void createPositiveRescheduleAdjustment() {
        Payment source = payment(uuid(101), uuid(31), PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 200.0);
        when(paymentRepository.findByBookingId(uuid(31))).thenReturn(List.of(source));
        when(processorRegistry.initiate(PaymentMethod.CARD, PaymentType.RESCHEDULE_PAYMENT, BigDecimal.valueOf(50)))
                .thenReturn(new PaymentInitiationResult("CARD-REF-2"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentRecordService.createRescheduleAdjustment(uuid(31), uuid(71), BigDecimal.valueOf(50));

        assertEquals(PaymentType.RESCHEDULE_PAYMENT, result.getPaymentType());
        assertEquals(50.0, result.getAmount());
        assertEquals(PaymentMethod.CARD, result.getPaymentMethod());
        assertNull(result.getRelatedPaymentId());
        assertEquals("CARD-REF-2", result.getProviderReference());
    }

    @Test
    @DisplayName("Creates a reschedule refund linked to the original booking charge")
    void createNegativeRescheduleAdjustment() {
        Payment source = payment(uuid(101), uuid(31), PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 200.0);
        when(paymentRepository.findByBookingId(uuid(31))).thenReturn(List.of(source));
        when(processorRegistry.initiate(PaymentMethod.CARD, PaymentType.RESCHEDULE_REFUND, BigDecimal.valueOf(25)))
                .thenReturn(new PaymentInitiationResult("CARD-REF-3"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentRecordService.createRescheduleAdjustment(uuid(31), uuid(-1), BigDecimal.valueOf(-25));

        assertEquals(PaymentType.RESCHEDULE_REFUND, result.getPaymentType());
        assertEquals(25.0, result.getAmount());
        assertEquals(uuid(101), result.getRelatedPaymentId());
        assertEquals(uuid(-1), result.getBookingSeriesId());
    }

    @Test
    @DisplayName("Rejects a reschedule adjustment when no original booking charge can be found")
    void createAdjustmentWithoutSourceCharge() {
        when(paymentRepository.findByBookingId(uuid(31))).thenReturn(List.of());

        assertThrows(ConflictException.class,
                () -> paymentRecordService.createRescheduleAdjustment(uuid(31), BigDecimal.TEN));

        verifyNoInteractions(processorRegistry);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Creates a cancellation refund from successful charges after applying the refund policy")
    void createCancellationRefund() {
        List<Payment> payments = List.of(
                payment(uuid(101), uuid(31), PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 200.0),
                payment(uuid(102), uuid(31), PaymentType.RESCHEDULE_PAYMENT, PaymentStatus.SUCCESS, 50.0),
                payment(uuid(103), uuid(31), PaymentType.BOOKING_PAYMENT, PaymentStatus.FAILED, 80.0),
                payment(uuid(104), uuid(31), PaymentType.CANCEL_REFUND, PaymentStatus.PENDING, 20.0));
        when(paymentRepository.findByBookingId(uuid(31))).thenReturn(payments);
        when(cancellationRefundPolicy.refundableAmount(any(), any())).thenReturn(BigDecimal.valueOf(230));
        when(processorRegistry.initiate(PaymentMethod.CARD, PaymentType.CANCEL_REFUND, BigDecimal.valueOf(230)))
                .thenReturn(new PaymentInitiationResult("REFUND-REF-4"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Payment> refund = paymentRecordService.createCancellationRefund(uuid(31), null);

        assertEquals(230.0, refund.orElseThrow().getAmount());
        assertEquals(PaymentType.CANCEL_REFUND, refund.orElseThrow().getPaymentType());
        assertEquals(uuid(31), refund.orElseThrow().getBookingId());
        assertNull(refund.orElseThrow().getBookingSeriesId());
        ArgumentCaptor<List<Payment>> chargesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Payment>> refundsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cancellationRefundPolicy).refundableAmount(chargesCaptor.capture(), refundsCaptor.capture());
        assertEquals(List.of(uuid(101), uuid(102)),
                chargesCaptor.getValue().stream().map(Payment::getId).toList());
        assertEquals(List.of(uuid(104)),
                refundsCaptor.getValue().stream().map(Payment::getId).toList());
    }

    @Test
    @DisplayName("Excludes a failed booking payment from cancellation refund eligibility")
    void excludesFailedCharge() {
        List<Payment> payments = List.of(
                payment(uuid(101), uuid(31), PaymentType.BOOKING_PAYMENT, PaymentStatus.FAILED, 200.0));
        when(paymentRepository.findByBookingId(uuid(31))).thenReturn(payments);

        Optional<Payment> refund = paymentRecordService.createCancellationRefund(uuid(31), null);

        assertEquals(Optional.empty(), refund);
        verifyNoInteractions(cancellationRefundPolicy, processorRegistry);
    }

    @Test
    @DisplayName("Does not create a refund when the policy finds no refundable balance")
    void noRefundableBalance() {
        List<Payment> payments = List.of(
                payment(uuid(101), uuid(31), PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 100.0));
        when(paymentRepository.findByBookingId(uuid(31))).thenReturn(payments);
        when(cancellationRefundPolicy.refundableAmount(payments, List.of())).thenReturn(BigDecimal.ZERO);

        Optional<Payment> refund = paymentRecordService.createCancellationRefund(uuid(31), null);

        assertEquals(Optional.empty(), refund);
        verifyNoInteractions(processorRegistry);
    }

    @Test
    @DisplayName("Creates one series-level refund using all payment records linked to the series")
    void createSeriesCancellationRefund() {
        List<Payment> payments = List.of(
                payment(uuid(101), uuid(31), uuid(71), PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 100.0),
                payment(uuid(102), uuid(32), uuid(71), PaymentType.BOOKING_PAYMENT, PaymentStatus.SUCCESS, 150.0));
        when(paymentRepository.findByBookingSeriesId(uuid(71))).thenReturn(payments);
        when(cancellationRefundPolicy.refundableAmount(payments, List.of())).thenReturn(BigDecimal.valueOf(250));
        when(processorRegistry.initiate(PaymentMethod.CARD, PaymentType.CANCEL_REFUND, BigDecimal.valueOf(250)))
                .thenReturn(new PaymentInitiationResult("SERIES-REF-5"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment refund = paymentRecordService.createSeriesCancellationRefund(uuid(71), List.of(uuid(31), uuid(32)))
                .orElseThrow();

        assertNull(refund.getBookingId());
        assertEquals(uuid(71), refund.getBookingSeriesId());
        assertEquals(250.0, refund.getAmount());
        assertNull(refund.getRelatedPaymentId());
        verify(paymentRepository, never()).findByBookingIdIn(any());
    }

    @Test
    @DisplayName("Finds payment records associated with one booking")
    void findPaymentsForBooking() {
        List<Payment> expected = List.of(payment(uuid(101), uuid(31), PaymentType.BOOKING_PAYMENT,
                PaymentStatus.PENDING, 100.0));
        when(paymentRepository.findByBookingId(uuid(31))).thenReturn(expected);

        assertEquals(expected, paymentRecordService.findPaymentsForBooking(uuid(31)));
    }

    private Payment payment(UUID id, UUID bookingId, PaymentType type, PaymentStatus status, Double amount) {
        return payment(id, bookingId, null, type, status, amount);
    }

    private Payment payment(UUID id, UUID bookingId, UUID seriesId,
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
