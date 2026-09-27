package com.househelper.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class BookingSeriesCancellationResponse {

    UUID seriesId;
    int cancelledOccurrences;
    List<UUID> cancelledBookingIds;
    UUID refundPaymentId;
}
