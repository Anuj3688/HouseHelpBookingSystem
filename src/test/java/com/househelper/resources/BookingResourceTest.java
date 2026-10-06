package com.househelper.resources;

import com.househelper.dto.BookingResponse;
import com.househelper.dto.BookingSeriesCancellationResponse;
import com.househelper.dto.RescheduleRequest;
import com.househelper.dto.UnifiedBookingRequest;
import com.househelper.model.BookingStatus;
import com.househelper.model.BookingType;
import com.househelper.model.PaymentMethod;
import com.househelper.model.SkillType;
import com.househelper.service.BookingSeriesService;
import com.househelper.service.BookingService;
import com.househelper.service.booking.BookingResult;
import com.househelper.service.booking.BookingStrategyRegistry;
import com.househelper.service.booking.BookingTypeStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingResourceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID SERIES_ID = UUID.randomUUID();

    @Mock
    private BookingService bookingService;

    @Mock
    private BookingSeriesService bookingSeriesService;

    @Mock
    private BookingStrategyRegistry bookingStrategyRegistry;

    @Mock
    private BookingTypeStrategy mockStrategy;

    private BookingResource bookingResource;

    @BeforeEach
    void setUp() {
        bookingResource = new BookingResource(bookingService, bookingSeriesService, bookingStrategyRegistry);
    }

    @Test
    @DisplayName("Single unified createBooking function dispatches polymorphically via strategy registry")
    void createBookingDispatchesPolymorphically() {
        UnifiedBookingRequest request = UnifiedBookingRequest.builder()
                .bookingType(BookingType.SCHEDULED)
                .customerId(CUSTOMER_ID)
                .locality("Indiranagar")
                .skill(SkillType.CLEANING)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .paymentMethod(PaymentMethod.CARD)
                .build();

        BookingResult mockResult = BookingResult.builder()
                .bookingType(BookingType.SCHEDULED)
                .bookings(List.of(BookingResponse.builder().id(BOOKING_ID).status(BookingStatus.PENDING_PAYMENT).build()))
                .build();

        when(bookingStrategyRegistry.getStrategy(BookingType.SCHEDULED)).thenReturn(mockStrategy);
        when(mockStrategy.execute(request)).thenReturn(mockResult);

        BookingResult result = bookingResource.createBooking(request);

        assertNotNull(result);
        assertEquals(BOOKING_ID, result.getId());
        assertEquals(BookingType.SCHEDULED, result.getBookingType());
        verify(bookingStrategyRegistry).getStrategy(BookingType.SCHEDULED);
        verify(mockStrategy).execute(request);
    }

    @Test
    @DisplayName("Legacy instant booking alias sets type and routes through strategy")
    void createInstantBookingRoutesThroughStrategy() {
        UnifiedBookingRequest request = UnifiedBookingRequest.builder()
                .customerId(CUSTOMER_ID)
                .locality("HSR")
                .skill(SkillType.COOKING)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        BookingResult mockResult = BookingResult.builder()
                .bookingType(BookingType.INSTANT)
                .bookings(List.of(BookingResponse.builder().id(BOOKING_ID).build()))
                .build();

        when(bookingStrategyRegistry.getStrategy(BookingType.INSTANT)).thenReturn(mockStrategy);
        when(mockStrategy.execute(any(UnifiedBookingRequest.class))).thenReturn(mockResult);

        BookingResult result = bookingResource.createInstantBooking(request);

        assertNotNull(result);
        assertEquals(BookingType.INSTANT, result.getBookingType());
        verify(bookingStrategyRegistry).getStrategy(BookingType.INSTANT);
    }

    @Test
    @DisplayName("Cancel series in consolidated BookingResource calls bookingSeriesService")
    void cancelSeriesCallsBookingSeriesService() {
        BookingSeriesCancellationResponse mockResponse = BookingSeriesCancellationResponse.builder()
                .seriesId(SERIES_ID)
                .cancelledOccurrences(3)
                .build();

        when(bookingSeriesService.cancelSeries(SERIES_ID)).thenReturn(mockResponse);

        BookingSeriesCancellationResponse response = bookingResource.cancelSeries(SERIES_ID);

        assertNotNull(response);
        assertEquals(SERIES_ID, response.getSeriesId());
        assertEquals(3, response.getCancelledOccurrences());
        verify(bookingSeriesService).cancelSeries(SERIES_ID);
    }

    @Test
    @DisplayName("Cancel individual booking calls bookingService")
    void cancelBookingCallsBookingService() {
        BookingResponse mockResponse = BookingResponse.builder().id(BOOKING_ID).status(BookingStatus.CANCELLED).build();
        when(bookingService.cancelBooking(BOOKING_ID)).thenReturn(mockResponse);

        BookingResponse response = bookingResource.cancelBooking(BOOKING_ID);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        verify(bookingService).cancelBooking(BOOKING_ID);
    }

    @Test
    @DisplayName("Reschedule booking calls bookingService")
    void rescheduleBookingCallsBookingService() {
        RescheduleRequest req = RescheduleRequest.builder()
                .newBookingDate(LocalDate.now().plusDays(2))
                .newStartTime(LocalTime.of(14, 0))
                .newEndTime(LocalTime.of(15, 0))
                .build();

        BookingResponse mockResponse = BookingResponse.builder().id(BOOKING_ID).build();
        when(bookingService.rescheduleBooking(BOOKING_ID, req)).thenReturn(mockResponse);

        BookingResponse response = bookingResource.rescheduleBooking(BOOKING_ID, req);

        assertEquals(BOOKING_ID, response.getId());
        verify(bookingService).rescheduleBooking(BOOKING_ID, req);
    }
}
