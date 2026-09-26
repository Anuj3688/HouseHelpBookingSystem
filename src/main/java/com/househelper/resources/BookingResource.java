package com.househelper.resources;

import com.househelper.dto.BookingRequest;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.RescheduleRequest;
import com.househelper.service.BookingService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create, reschedule, and cancel customer bookings.")
public class BookingResource {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a booking", description = "Allocates the lowest-priced available helper for an existing customer.")
    public BookingResponse createBooking(@Valid @RequestBody BookingRequest request) {
        return bookingService.createBooking(request);
    }

    @PutMapping("/{bookingId}/reschedule")
    @Operation(summary = "Reschedule a booking", description = "Moves a booking to an available slot and records any price difference.")
    public BookingResponse rescheduleBooking(@PathVariable Long bookingId,
                                             @Valid @RequestBody RescheduleRequest request) {
        return bookingService.rescheduleBooking(bookingId, request);
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a booking", description = "Cancels a booking, releases its slot, and updates payment records.")
    public BookingResponse cancelBooking(@PathVariable Long bookingId) {
        return bookingService.cancelBooking(bookingId);
    }
}