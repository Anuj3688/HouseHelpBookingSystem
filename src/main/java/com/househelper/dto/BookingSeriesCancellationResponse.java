package com.househelper.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BookingSeriesCancellationResponse {

    Long seriesId;
    int cancelledOccurrences;
    List<Long> cancelledBookingIds;
    Long refundPaymentId;
}
