package com.househelper.service.booking;

import com.househelper.dto.BookingResponse;
import com.househelper.dto.InstantBookingRequest;
import com.househelper.dto.UnifiedBookingRequest;
import com.househelper.exception.InvalidRequestException;
import com.househelper.model.BookingType;
import com.househelper.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Concrete strategy for handling INSTANT bookings (discovering the earliest available slot today).
 */
@Component
@RequiredArgsConstructor
public class InstantBookingStrategy implements BookingTypeStrategy {

    private final BookingService bookingService;

    @Override
    public BookingType bookingType() {
        return BookingType.INSTANT;
    }

    @Override
    public BookingResult execute(BookingCommand command) {
        InstantBookingRequest request = toInstantBookingRequest(command);
        BookingResponse response = bookingService.createInstantBooking(request);
        return BookingResult.single(response);
    }

    private InstantBookingRequest toInstantBookingRequest(BookingCommand command) {
        if (command instanceof InstantBookingRequest request) {
            return request;
        }
        if (command instanceof UnifiedBookingRequest unified) {
            InstantBookingRequest request = new InstantBookingRequest();
            request.setCustomerId(unified.getCustomerId());
            request.setLocality(unified.getLocality());
            request.setSkill(unified.getSkill());
            request.setPaymentMethod(unified.getPaymentMethod());
            return request;
        }
        throw new InvalidRequestException("Unsupported command type for Instant booking: " + command.getClass().getSimpleName());
    }
}
