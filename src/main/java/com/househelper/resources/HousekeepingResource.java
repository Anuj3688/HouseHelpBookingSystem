package com.househelper.resources;

import com.househelper.dto.AvailableSlotResponse;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.CustomerResponse;
import com.househelper.dto.HelperSearchResponse;
import com.househelper.dto.PaymentResponse;
import com.househelper.service.HousekeepingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/housekeeping")
@RequiredArgsConstructor
@Tag(name = "Housekeeping", description = "Read-only data views for local testing and inspection.")
public class HousekeepingResource {

    private final HousekeepingService housekeepingService;

    @GetMapping("/available-helpers")
    @Operation(summary = "List helpers with open slots", description = "Returns each helper who has at least one available slot.")
    public List<HelperSearchResponse> getAvailableHelpers() {
        return housekeepingService.getAvailableHelpers();
    }

    @GetMapping("/helpers")
    @Operation(summary = "List all registered helpers", description = "Returns all helpers, including those without currently available slots.")
    public List<HelperSearchResponse> getRegisteredHelpers() {
        return housekeepingService.getRegisteredHelpers();
    }

    @GetMapping("/customers")
    @Operation(summary = "List registered customers", description = "Returns all customer records for testing and inspection.")
    public List<CustomerResponse> getRegisteredCustomers() {
        return housekeepingService.getRegisteredCustomers();
    }

    @GetMapping("/available-slots")
    @Operation(summary = "List open availability slots", description = "Returns all slots currently marked AVAILABLE, across all dates.")
    public List<AvailableSlotResponse> getAvailableSlots() {
        return housekeepingService.getAvailableSlots();
    }

    @GetMapping("/bookings")
    @Operation(summary = "List all bookings", description = "Returns every booking with its customer and assigned helper IDs.")
    public List<BookingResponse> getAllBookings() {
        return housekeepingService.getAllBookings();
    }

    @GetMapping("/payments")
    @Operation(summary = "List all payments", description = "Returns every payment record and its current simulated payment status.")
    public List<PaymentResponse> getAllPayments() {
        return housekeepingService.getAllPayments();
    }
}
