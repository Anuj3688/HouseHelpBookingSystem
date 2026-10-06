package com.househelper.service.booking;

import com.househelper.model.BookingType;
import com.househelper.model.PaymentMethod;
import com.househelper.model.SkillType;

import java.util.UUID;

/**
 * Common abstraction representing a booking request command.
 * All booking types (Scheduled, Instant, Recurring, or future variants)
 * share this common command contract.
 */
public interface BookingCommand {

    BookingType getBookingType();

    UUID getCustomerId();

    String getLocality();

    SkillType getSkill();

    PaymentMethod getPaymentMethod();
}
