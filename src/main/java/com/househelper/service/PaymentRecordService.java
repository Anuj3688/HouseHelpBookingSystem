package com.househelper.service;

import com.househelper.model.Payment;
import com.househelper.model.PaymentStatus;
import com.househelper.model.PaymentType;
import com.househelper.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentRecordService {

    private final PaymentRepository paymentRepository;
    private final CancellationRefundPolicy cancellationRefundPolicy;

    @Transactional
    public Payment createBookingPayment(Long bookingId, BigDecimal amount, String paymentMethod) {
        return createBookingPayment(bookingId, null, amount, paymentMethod);
    }

    @Transactional
    public Payment createBookingPayment(Long bookingId, Long seriesId, BigDecimal amount, String paymentMethod) {
        return savePayment(bookingId, seriesId, amount, paymentMethod, PaymentType.BOOKING_PAYMENT, null);
    }

    @Transactional
    public Payment createRescheduleAdjustment(Long bookingId, BigDecimal delta) {
        return createRescheduleAdjustment(bookingId, null, delta);
    }

    @Transactional
    public Payment createRescheduleAdjustment(Long bookingId, Long seriesId, BigDecimal delta) {
        if (delta.signum() == 0) {
            return null;
        }

        List<Payment> bookingPayments = paymentRepository.findByBookingId(bookingId);
        Payment sourcePayment = bookingPayments.stream()
                .filter(payment -> payment.getPaymentType() == PaymentType.BOOKING_PAYMENT)
                .findFirst()
                .orElse(null);
        String paymentMethod = sourcePayment == null ? "UNSPECIFIED" : sourcePayment.getPaymentMethod();
        PaymentType paymentType = delta.signum() > 0
                ? PaymentType.RESCHEDULE_PAYMENT
                : PaymentType.RESCHEDULE_REFUND;
        Long relatedPaymentId = delta.signum() < 0 && sourcePayment != null
                ? sourcePayment.getId()
                : null;
        return savePayment(bookingId, seriesId, delta.abs(), paymentMethod, paymentType, relatedPaymentId);
    }

    @Transactional
    public Optional<Payment> createCancellationRefund(Long bookingId, Long excludedPaymentId) {
        return createCancellationRefund(paymentRepository.findByBookingId(bookingId), bookingId, null,
                excludedPaymentId);
    }

    @Transactional
    public Optional<Payment> createCancellationRefund(Long bookingId, Long seriesId, Long excludedPaymentId) {
        return createCancellationRefund(paymentRepository.findByBookingId(bookingId), bookingId, seriesId,
                excludedPaymentId);
    }

    @Transactional
    public Optional<Payment> createSeriesCancellationRefund(Long seriesId, List<Long> bookingIds) {
        List<Payment> payments = bookingIds.isEmpty()
                ? List.of()
                : paymentRepository.findByBookingIdIn(bookingIds);
        return createCancellationRefund(payments, null, seriesId, null);
    }

    private Optional<Payment> createCancellationRefund(List<Payment> payments, Long bookingId,
                                                       Long seriesId, Long excludedPaymentId) {
        List<Payment> successfulCharges = payments.stream()
                .filter(payment -> excludedPaymentId == null || !payment.getId().equals(excludedPaymentId))
                .filter(payment -> payment.getPaymentType() == PaymentType.BOOKING_PAYMENT
                        || payment.getPaymentType() == PaymentType.RESCHEDULE_PAYMENT)
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.SUCCESS)
                .toList();
        if (successfulCharges.isEmpty()) {
            return Optional.empty();
        }

        List<Payment> existingRefunds = payments.stream()
                .filter(payment -> payment.getPaymentType() == PaymentType.CANCEL_REFUND
                        || payment.getPaymentType() == PaymentType.RESCHEDULE_REFUND)
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.PENDING
                        || payment.getPaymentStatus() == PaymentStatus.SUCCESS)
                .toList();
        BigDecimal refundableAmount = cancellationRefundPolicy.refundableAmount(successfulCharges, existingRefunds);
        if (refundableAmount.signum() <= 0) {
            return Optional.empty();
        }

        String paymentMethod = successfulCharges.getFirst().getPaymentMethod();
        Long relatedPaymentId = successfulCharges.size() == 1
                ? successfulCharges.getFirst().getId()
                : null;
        return Optional.of(savePayment(bookingId, seriesId, refundableAmount, paymentMethod,
                PaymentType.CANCEL_REFUND, relatedPaymentId));
    }

    @Transactional(readOnly = true)
    public List<Payment> findPaymentsForBooking(Long bookingId) {
        return paymentRepository.findByBookingId(bookingId);
    }

    private Payment savePayment(Long bookingId, Long seriesId, BigDecimal amount, String paymentMethod,
                                PaymentType paymentType, Long relatedPaymentId) {
        return paymentRepository.save(Payment.builder()
                .bookingId(bookingId)
                .bookingSeriesId(seriesId)
                .paymentType(paymentType)
                .relatedPaymentId(relatedPaymentId)
                .amount(amount.doubleValue())
                .paymentMethod(paymentMethod.trim())
                .paymentStatus(PaymentStatus.PENDING)
                .build());
    }
}
