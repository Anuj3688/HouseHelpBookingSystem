package com.househelper.service.booking;

import com.househelper.model.BookingType;

/**
 * Common strategy abstraction for processing different booking types.
 * Adding a new booking type requires implementing this interface and
 * registering the implementation as a Spring component.
 */
public interface BookingTypeStrategy {

    BookingType bookingType();

    BookingResult execute(BookingCommand command);
}
