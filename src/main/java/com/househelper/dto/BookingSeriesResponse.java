package com.househelper.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class BookingSeriesResponse {

    UUID seriesId;
    int requestedOccurrences;
    List<BookingResponse> createdBookings;
    List<LocalDate> unavailableDates;
}
