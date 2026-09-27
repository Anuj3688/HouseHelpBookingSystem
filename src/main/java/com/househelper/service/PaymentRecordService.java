package com.househelper.service;

import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentStatus;
import com.househelper.model.PaymentType;
import com.househelper.repository.PaymentRepository;
import com.househelper.service.payment.PaymentInitiationResult;
import com.househelper.service.payment.PaymentMethodProcessorRegistry;
import com.househelper.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentRecordService {

    private final PaymentRepository paymentRepository;
    private final CancellationRefundPolicy cancellationRefundPolicy;
    private final PaymentMethodProcessorRegistry paymentMethodProcessorRegistry;

    @Transactional
    public Payment createBookingPayment(UUID bookingId, BigDecimal amount, PaymentMethod paymentMethod) {
        return createBookingPayment(bookingId, null, amount, paymentMethod);
    }

    @Transactional
    public Payment createBookingPayment(UUID bookingId, UUID seriesId, BigDecimal amount, PaymentMethod paymentMethod) {
        return savePayment(bookingId, seriesId, amount, paymentMethod, PaymentType.BOOKING_PAYMENT, null);
    }

    @Transactional
    public Payment createRescheduleAdjustment(UUID bookingId, BigDecimal delta) {
        return createRescheduleAdjustment(bookingId, null, delta);
    }

    @Transactional
    public Payment createRescheduleAdjustment(UUID bookingId, UUID seriesId, BigDecimal delta) {
        if (delta.signum() == 0) {
            return null;
        }

        List<Payment> bookingPayments = paymentRepository.findByBookingId(bookingId);
        Payment sourcePayment = bookingPayments.stream()
                .filter(payment -> payment.getPaymentType() == PaymentType.BOOKING_PAYMENT)
                .findFirst()
                .orElse(null);
        if (sourcePayment == null) {
            throw new ConflictException("Cannot determine the payment method for this reschedule adjustment.");
        }
        PaymentMethod paymentMethod = sourcePayment.getPaymentMethod();
        PaymentType paymentType = delta.signum() > 0
                ? PaymentType.RESCHEDULE_PAYMENT
                : PaymentType.RESCHEDULE_REFUND;
        UUID relatedPaymentId = delta.signum() < 0 && sourcePayment != null
                ? sourcePayment.getId()
                : null;
        return savePayment(bookingId, seriesId, delta.abs(), paymentMethod, paymentType, relatedPaymentId);
    }

    @Transactional
    public Optional<Payment> createCancellationRefund(UUID bookingId, UUID excludedPaymentId) {
        return createCancellationRefund(paymentRepository.findByBookingId(bookingId), bookingId, null,
                excludedPaymentId);
    }

    @Transactional
    public Optional<Payment> createCancellationRefund(UUID bookingId, UUID seriesId, UUID excludedPaymentId) {
        return createCancellationRefund(paymentRepository.findByBookingId(bookingId), bookingId, seriesId,
                excludedPaymentId);
    }

    @Transactional
    public Optional<Payment> createSeriesCancellationRefund(UUID seriesId, List<UUID> bookingIds) {
        List<Payment> payments = paymentRepository.findByBookingSeriesId(seriesId);
        if (payments.isEmpty() && !bookingIds.isEmpty()) {
            payments = paymentRepository.findByBookingIdIn(bookingIds);
        }
        return createCancellationRefund(payments, null, seriesId, null);
    }

    private Optional<Payment> createCancellationRefund(List<Payment> payments, UUID bookingId,
                                                       UUID seriesId, UUID excludedPaymentId) {
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

        PaymentMethod paymentMethod = successfulCharges.getFirst().getPaymentMethod();
        UUID relatedPaymentId = successfulCharges.size() == 1
                ? successfulCharges.getFirst().getId()
                : null;
        return Optional.of(savePayment(bookingId, seriesId, refundableAmount, paymentMethod,
                PaymentType.CANCEL_REFUND, relatedPaymentId));
    }

    @Transactional(readOnly = true)
    public List<Payment> findPaymentsForBooking(UUID bookingId) {
        return paymentRepository.findByBookingId(bookingId);
    }

    private Payment savePayment(UUID bookingId, UUID seriesId, BigDecimal amount, PaymentMethod paymentMethod,
                                PaymentType paymentType, UUID relatedPaymentId) {
        PaymentInitiationResult initiation = paymentMethodProcessorRegistry.initiate(
                paymentMethod, paymentType, amount);
        return paymentRepository.save(Payment.builder()
                .bookingId(bookingId)
                .bookingSeriesId(seriesId)
                .providerReference(initiation.providerReference())
                .paymentType(paymentType)
                .relatedPaymentId(relatedPaymentId)
                .amount(amount.doubleValue())
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.PENDING)
                .build());
    }
}
