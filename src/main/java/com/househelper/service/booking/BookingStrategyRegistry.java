package com.househelper.service.booking;

import com.househelper.exception.InvalidRequestException;
import com.househelper.model.BookingType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry holding all available booking strategies.
 * Mirrors the PaymentMethodProcessorRegistry pattern.
 */
@Component
public class BookingStrategyRegistry {

    private final Map<BookingType, BookingTypeStrategy> strategies;

    public BookingStrategyRegistry(List<BookingTypeStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(BookingTypeStrategy::bookingType, Function.identity()));
    }

    public BookingTypeStrategy getStrategy(BookingType type) {
        if (type == null) {
            throw new InvalidRequestException("Booking type must not be null.");
        }
        BookingTypeStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new InvalidRequestException("No booking strategy registered for booking type: " + type);
        }
        return strategy;
    }

    public boolean supports(BookingType type) {
        return type != null && strategies.containsKey(type);
    }
}
