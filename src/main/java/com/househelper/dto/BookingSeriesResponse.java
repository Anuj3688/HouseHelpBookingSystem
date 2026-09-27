package com.househelper.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Value
@Builder
public class BookingSeriesResponse {

    UUID seriesId;
    int requestedOccurrences;
    List<BookingResponse> createdBookings;
    List<LocalDate> unavailableDates;
    Set<DayOfWeek> recurrenceDays;
}
