package com.househelper.service;

import com.househelper.dto.PaymentResponse;
import com.househelper.dto.PaymentStatusUpdateRequest;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.model.Payment;
import com.househelper.model.PaymentStatus;
import com.househelper.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EventPublisherService eventPublisherService;

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        return toResponse(requirePayment(paymentId));
    }

    @Transactional
    public PaymentResponse updatePaymentStatus(Long paymentId, PaymentStatusUpdateRequest request) {
        PaymentStatus requestedStatus = request.getStatus();
        if (requestedStatus != PaymentStatus.SUCCESS && requestedStatus != PaymentStatus.FAILED) {
            throw new InvalidRequestException("Mock payment processing only accepts SUCCESS or FAILED.");
        }

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " was not found."));
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Only pending payments can be marked as successful or failed.");
        }

        payment.setPaymentStatus(requestedStatus);
        PaymentResponse response = toResponse(paymentRepository.save(payment));
        eventPublisherService.publishEvent("PAYMENT_STATUS_UPDATED", "Payment",
                payment.getId().toString(), response);
        return response;
    }

    private Payment requirePayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " was not found."));
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .bookingId(payment.getBookingId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .build();
    }
}
