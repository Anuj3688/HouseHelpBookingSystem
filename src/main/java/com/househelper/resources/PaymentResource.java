package com.househelper.resources;

import com.househelper.dto.PaymentResponse;
import com.househelper.dto.PaymentStatusUpdateRequest;
import com.househelper.service.PaymentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Inspect payment records and simulate payment-provider outcomes.")
public class PaymentResource {

    private final PaymentService paymentService;

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get a payment", description = "Returns a payment record by its ID.")
    public PaymentResponse getPayment(@PathVariable Long paymentId) {
        return paymentService.getPayment(paymentId);
    }

    @PatchMapping("/{paymentId}/status")
    @Operation(summary = "Simulate a payment outcome", description = "Transitions a pending payment to SUCCESS or FAILED.")
    public PaymentResponse updatePaymentStatus(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentStatusUpdateRequest request) {
        return paymentService.updatePaymentStatus(paymentId, request);
    }
}
