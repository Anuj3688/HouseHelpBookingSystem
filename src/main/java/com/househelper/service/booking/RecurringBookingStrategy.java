package com.househelper.service.booking;

import com.househelper.dto.BookingSeriesRequest;
import com.househelper.dto.BookingSeriesResponse;
import com.househelper.dto.UnifiedBookingRequest;
import com.househelper.exception.InvalidRequestException;
import com.househelper.model.BookingType;
import com.househelper.service.BookingSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Concrete strategy for handling RECURRING bookings (creating a repeating series of slots).
 */
@Component
@RequiredArgsConstructor
public class RecurringBookingStrategy implements BookingTypeStrategy {

    private final BookingSeriesService bookingSeriesService;

    @Override
    public BookingType bookingType() {
        return BookingType.RECURRING;
    }

    @Override
    public BookingResult execute(BookingCommand command) {
        BookingSeriesRequest request = toBookingSeriesRequest(command);
        BookingSeriesResponse response = bookingSeriesService.createWeeklySeries(request);
        return BookingResult.series(response);
    }

    private BookingSeriesRequest toBookingSeriesRequest(BookingCommand command) {
        if (command instanceof BookingSeriesRequest request) {
            return request;
        }
        if (command instanceof UnifiedBookingRequest unified) {
            if (unified.getBookingDate() == null || unified.getStartTime() == null || unified.getEndTime() == null
                    || unified.getOccurrenceCount() == null) {
                throw new InvalidRequestException("Recurring bookings require bookingDate (as startDate), startTime, endTime, and occurrenceCount.");
            }
            BookingSeriesRequest request = new BookingSeriesRequest();
            request.setCustomerId(unified.getCustomerId());
            request.setLocality(unified.getLocality());
            request.setSkill(unified.getSkill());
            request.setStartDate(unified.getBookingDate());
            request.setStartTime(unified.getStartTime());
            request.setEndTime(unified.getEndTime());
            request.setPaymentMethod(unified.getPaymentMethod());
            request.setOccurrenceCount(unified.getOccurrenceCount());
            request.setRecurrenceDays(unified.getRecurrenceDays());
            return request;
        }
        throw new InvalidRequestException("Unsupported command type for Recurring booking: " + command.getClass().getSimpleName());
    }
}
