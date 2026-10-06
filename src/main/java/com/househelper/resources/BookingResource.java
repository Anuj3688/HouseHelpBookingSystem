package com.househelper.resources;

import com.househelper.dto.BookingResponse;
import com.househelper.dto.BookingSeriesCancellationResponse;
import com.househelper.dto.RescheduleRequest;
import com.househelper.dto.UnifiedBookingRequest;
import com.househelper.model.BookingType;
import com.househelper.service.BookingSeriesService;
import com.househelper.service.BookingService;
import com.househelper.service.booking.BookingResult;
import com.househelper.service.booking.BookingStrategyRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Consolidated resource for all booking operations (Instant, Scheduled, and Recurring Series).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create, reschedule, and cancel customer bookings and recurring series.")
public class BookingResource {

    private final BookingService bookingService;
    private final BookingSeriesService bookingSeriesService;
    private final BookingStrategyRegistry bookingStrategyRegistry;

    /**
     * Single polymorphic booking creation entry point.
     * Routes automatically to ScheduledBookingStrategy, InstantBookingStrategy,
     * or RecurringBookingStrategy based on the requested booking type.
     */
    @PostMapping({"/bookings", "/booking-series"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking", description = "Unified polymorphic entry point creating any booking type (SCHEDULED, INSTANT, RECURRING) via its strategy.")
    public BookingResult createBooking(@Valid @RequestBody UnifiedBookingRequest request) {
        return bookingStrategyRegistry.getStrategy(request.getBookingType())
                .execute(request);
    }

    /**
     * Reschedule an existing booking.
     */
    @PutMapping("/bookings/{bookingId}/reschedule")
    @Operation(summary = "Reschedule a booking", description = "Moves a booking to an available slot and records any price difference.")
    public BookingResponse rescheduleBooking(@PathVariable UUID bookingId,
                                             @Valid @RequestBody RescheduleRequest request) {
        return bookingService.rescheduleBooking(bookingId, request);
    }

    /**
     * Cancel an individual booking occurrence.
     */
    @PostMapping("/bookings/{bookingId}/cancel")
    @Operation(summary = "Cancel a booking", description = "Cancels a single booking, releases its slot, and updates payment records.")
    public BookingResponse cancelBooking(@PathVariable UUID bookingId) {
        return bookingService.cancelBooking(bookingId);
    }

    /**
     * Cancel an entire recurring booking series.
     * Consolidated in the common Booking resource.
     */
    @PostMapping({"/bookings/series/{seriesId}/cancel", "/booking-series/{seriesId}/cancel"})
    @Operation(summary = "Cancel a booking series", description = "Cancels all active occurrences in a recurring series and releases their slots.")
    public BookingSeriesCancellationResponse cancelSeries(@PathVariable UUID seriesId) {
        return bookingSeriesService.cancelSeries(seriesId);
    }

    // --- Legacy convenience alias ---

    @PostMapping("/bookings/instant")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an instant booking (Legacy convenience endpoint)", description = "Convenience endpoint setting bookingType to INSTANT.")
    public BookingResult createInstantBooking(@Valid @RequestBody UnifiedBookingRequest request) {
        request.setBookingType(BookingType.INSTANT);
        return createBooking(request);
    }
}
