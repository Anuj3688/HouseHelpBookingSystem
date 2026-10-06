package com.househelper.service.booking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.BookingSeriesResponse;
import com.househelper.model.BookingStatus;
import com.househelper.model.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Unified result produced by executing any booking strategy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResult {

    private BookingType bookingType;
    private UUID seriesId;

    @Builder.Default
    private List<BookingResponse> bookings = Collections.emptyList();

    @Builder.Default
    private List<LocalDate> unavailableDates = Collections.emptyList();

    private Integer requestedOccurrences;
    private Set<DayOfWeek> recurrenceDays;

    public BookingResponse getPrimaryBooking() {
        return bookings != null && !bookings.isEmpty() ? bookings.get(0) : null;
    }

    @JsonProperty("id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public UUID getId() {
        return getPrimaryBooking() != null ? getPrimaryBooking().getId() : null;
    }

    @JsonProperty("paymentId")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public UUID getPaymentId() {
        return getPrimaryBooking() != null ? getPrimaryBooking().getPaymentId() : null;
    }

    @JsonProperty("assignedHelperId")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public UUID getAssignedHelperId() {
        return getPrimaryBooking() != null ? getPrimaryBooking().getAssignedHelperId() : null;
    }

    @JsonProperty("totalAmount")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double getTotalAmount() {
        return getPrimaryBooking() != null ? getPrimaryBooking().getTotalAmount() : null;
    }

    @JsonProperty("status")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public BookingStatus getStatus() {
        return getPrimaryBooking() != null ? getPrimaryBooking().getStatus() : null;
    }

    @JsonProperty("createdBookings")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<BookingResponse> getCreatedBookings() {
        return bookingType == BookingType.RECURRING ? bookings : null;
    }

    public static BookingResult single(BookingResponse booking) {
        return BookingResult.builder()
                .bookingType(booking.getBookingType())
                .seriesId(booking.getSeriesId())
                .bookings(List.of(booking))
                .build();
    }

    public static BookingResult series(BookingSeriesResponse seriesResponse) {
        return BookingResult.builder()
                .bookingType(BookingType.RECURRING)
                .seriesId(seriesResponse.getSeriesId())
                .bookings(seriesResponse.getCreatedBookings())
                .unavailableDates(seriesResponse.getUnavailableDates())
                .requestedOccurrences(seriesResponse.getRequestedOccurrences())
                .recurrenceDays(seriesResponse.getRecurrenceDays())
                .build();
    }

    public BookingSeriesResponse toSeriesResponse() {
        return BookingSeriesResponse.builder()
                .seriesId(seriesId)
                .requestedOccurrences(requestedOccurrences != null ? requestedOccurrences : (bookings != null ? bookings.size() : 0))
                .createdBookings(bookings)
                .unavailableDates(unavailableDates)
                .recurrenceDays(recurrenceDays)
                .build();
    }
}
