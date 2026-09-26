package com.househelper.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class BookingSeriesResponse {

    Long seriesId;
    int requestedOccurrences;
    List<BookingResponse> createdBookings;
    List<LocalDate> unavailableDates;
}
