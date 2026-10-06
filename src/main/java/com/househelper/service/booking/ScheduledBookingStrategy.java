package com.househelper.service.booking;

import com.househelper.dto.BookingRequest;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.UnifiedBookingRequest;
import com.househelper.exception.InvalidRequestException;
import com.househelper.model.BookingType;
import com.househelper.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Concrete strategy for handling SCHEDULED bookings for a specific future date and time.
 */
@Component
@RequiredArgsConstructor
public class ScheduledBookingStrategy implements BookingTypeStrategy {

    private final BookingService bookingService;

    @Override
    public BookingType bookingType() {
        return BookingType.SCHEDULED;
    }

    @Override
    public BookingResult execute(BookingCommand command) {
        BookingRequest request = toBookingRequest(command);
        BookingResponse response = bookingService.createBooking(request);
        return BookingResult.single(response);
    }

    private BookingRequest toBookingRequest(BookingCommand command) {
        if (command instanceof BookingRequest request) {
            return request;
        }
        if (command instanceof UnifiedBookingRequest unified) {
            if (unified.getBookingDate() == null || unified.getStartTime() == null || unified.getEndTime() == null) {
                throw new InvalidRequestException("Scheduled bookings require bookingDate, startTime, and endTime.");
            }
            return BookingRequest.builder()
                    .customerId(unified.getCustomerId())
                    .locality(unified.getLocality())
                    .skill(unified.getSkill())
                    .bookingDate(unified.getBookingDate())
                    .startTime(unified.getStartTime())
                    .endTime(unified.getEndTime())
                    .paymentMethod(unified.getPaymentMethod())
                    .bookingType(BookingType.SCHEDULED)
                    .build();
        }
        throw new InvalidRequestException("Unsupported command type for Scheduled booking: " + command.getClass().getSimpleName());
    }
}
