package com.househelper.service;

import com.househelper.dto.BookingRequest;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.BookingSeriesCancellationResponse;
import com.househelper.dto.BookingSeriesRequest;
import com.househelper.dto.BookingSeriesResponse;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.exception.SlotUnavailableException;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Booking;
import com.househelper.model.BookingSeries;
import com.househelper.model.BookingSeriesStatus;
import com.househelper.model.BookingStatus;
import com.househelper.model.Customer;
import com.househelper.model.HelperAvailability;
import com.househelper.model.Payment;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.BookingSeriesRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingSeriesServiceTest {

    private static final Long SERIES_ID = 51L;
    private static final Long CUSTOMER_ID = 11L;
    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 5);
    private static final LocalTime START_TIME = LocalTime.of(9, 0);
    private static final LocalTime END_TIME = LocalTime.of(10, 0);

    @Mock
    private BookingSeriesRepository bookingSeriesRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private HelperAvailabilityRepository availabilityRepository;

    @Mock
    private BookingService bookingService;

    @Mock
    private PaymentRecordService paymentRecordService;

    @Mock
    private EventPublisherService eventPublisherService;

    @InjectMocks
    private BookingSeriesService bookingSeriesService;

    @Test
    @DisplayName("Creates available weekly occurrences and returns unavailable dates without rejecting the series")
    void createWeeklySeriesPartialAvailability() {
        Customer customer = customer();
        BookingSeries series = series(customer, BookingSeriesStatus.ACTIVE);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(bookingSeriesRepository.save(any(BookingSeries.class))).thenReturn(series);
        when(bookingService.createRecurringBookingOccurrence(eq(series), any(BookingRequest.class)))
                .thenReturn(bookingResponse(101L, SERIES_ID))
                .thenThrow(new SlotUnavailableException("No helper"))
                .thenReturn(bookingResponse(103L, SERIES_ID));

        BookingSeriesResponse response = bookingSeriesService.createWeeklySeries(request(3, START_TIME, END_TIME));

        assertEquals(SERIES_ID, response.getSeriesId());
        assertEquals(3, response.getRequestedOccurrences());
        assertEquals(List.of(101L, 103L), response.getCreatedBookings().stream().map(BookingResponse::getId).toList());
        assertEquals(List.of(START_DATE.plusWeeks(1)), response.getUnavailableDates());
        ArgumentCaptor<BookingRequest> requestCaptor = ArgumentCaptor.forClass(BookingRequest.class);
        verify(bookingService, org.mockito.Mockito.times(3))
                .createRecurringBookingOccurrence(eq(series), requestCaptor.capture());
        assertEquals(List.of(START_DATE, START_DATE.plusWeeks(1), START_DATE.plusWeeks(2)),
                requestCaptor.getAllValues().stream().map(BookingRequest::getBookingDate).toList());
        verify(eventPublisherService).publishEvent(
                eq("BOOKING_SERIES_CREATED"), eq("BookingSeries"), eq(SERIES_ID.toString()),
                isNull(), eq(CUSTOMER_ID), isNull(), isNull(), eq(SERIES_ID), any());
    }

    @Test
    @DisplayName("Reports a missing customer without creating a booking series")
    void createSeriesCustomerMissing() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingSeriesService.createWeeklySeries(
                request(2, START_TIME, END_TIME)));

        verifyNoInteractions(bookingSeriesRepository, bookingService, paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Rejects a weekly series whose end time is not after its start time")
    void createSeriesInvalidPeriod() {
        assertThrows(InvalidRequestException.class, () -> bookingSeriesService.createWeeklySeries(
                request(2, START_TIME, START_TIME)));

        verifyNoInteractions(customerRepository, bookingSeriesRepository, bookingService, eventPublisherService);
    }

    @Test
    @DisplayName("Propagates non-availability failures instead of reporting them as unavailable dates")
    void createSeriesUnexpectedFailure() {
        Customer customer = customer();
        BookingSeries series = series(customer, BookingSeriesStatus.ACTIVE);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(bookingSeriesRepository.save(any(BookingSeries.class))).thenReturn(series);
        when(bookingService.createRecurringBookingOccurrence(eq(series), any(BookingRequest.class)))
                .thenThrow(new IllegalStateException("Payment processor failed"));

        assertThrows(IllegalStateException.class, () -> bookingSeriesService.createWeeklySeries(
                request(2, START_TIME, END_TIME)));

        verify(eventPublisherService, never()).publishEvent(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cancels active series occurrences, releases slots, and creates one consolidated refund")
    void cancelSeries() {
        BookingSeries series = series(customer(), BookingSeriesStatus.ACTIVE);
        Booking first = booking(101L, BookingStatus.CONFIRMED, START_DATE);
        Booking second = booking(102L, BookingStatus.PENDING_PAYMENT, START_DATE.plusWeeks(1));
        Booking alreadyCancelled = booking(103L, BookingStatus.CANCELLED, START_DATE.plusWeeks(2));
        HelperAvailability firstSlot = slot(201L);
        HelperAvailability secondSlot = slot(202L);
        Payment refund = Payment.builder().id(301L).build();
        when(bookingSeriesRepository.findByIdForUpdate(SERIES_ID)).thenReturn(Optional.of(series));
        when(bookingRepository.findByBookingSeries_IdOrderByBookingDateAsc(SERIES_ID))
                .thenReturn(List.of(first, second, alreadyCancelled));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(21L, first.getBookingDate(), START_TIME))
                .thenReturn(Optional.of(firstSlot));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(21L, second.getBookingDate(), START_TIME))
                .thenReturn(Optional.of(secondSlot));
        when(paymentRecordService.createSeriesCancellationRefund(SERIES_ID, List.of(101L, 102L)))
                .thenReturn(Optional.of(refund));

        BookingSeriesCancellationResponse response = bookingSeriesService.cancelSeries(SERIES_ID);

        assertEquals(BookingSeriesStatus.CANCELLED, series.getStatus());
        assertEquals(BookingStatus.CANCELLED, first.getStatus());
        assertEquals(BookingStatus.CANCELLED, second.getStatus());
        assertEquals(BookingStatus.CANCELLED, alreadyCancelled.getStatus());
        assertEquals(AvailabilityStatus.AVAILABLE, firstSlot.getStatus());
        assertEquals(AvailabilityStatus.AVAILABLE, secondSlot.getStatus());
        assertEquals(2, response.getCancelledOccurrences());
        assertEquals(List.of(101L, 102L), response.getCancelledBookingIds());
        assertEquals(301L, response.getRefundPaymentId());
        verify(bookingRepository).saveAll(List.of(first, second));
        verify(paymentRecordService).createSeriesCancellationRefund(SERIES_ID, List.of(101L, 102L));
        verify(eventPublisherService).publishEvent(
                eq("BOOKING_SERIES_CANCELLED"), eq("BookingSeries"), eq(SERIES_ID.toString()),
                isNull(), eq(CUSTOMER_ID), eq(301L), isNull(), eq(SERIES_ID), any());
    }

    @Test
    @DisplayName("Cancels a series without creating a refund when there are no active occurrences")
    void cancelSeriesWithoutActiveBookings() {
        BookingSeries series = series(customer(), BookingSeriesStatus.ACTIVE);
        Booking cancelled = booking(103L, BookingStatus.CANCELLED, START_DATE);
        when(bookingSeriesRepository.findByIdForUpdate(SERIES_ID)).thenReturn(Optional.of(series));
        when(bookingRepository.findByBookingSeries_IdOrderByBookingDateAsc(SERIES_ID))
                .thenReturn(List.of(cancelled));
        when(paymentRecordService.createSeriesCancellationRefund(SERIES_ID, List.of()))
                .thenReturn(Optional.empty());

        BookingSeriesCancellationResponse response = bookingSeriesService.cancelSeries(SERIES_ID);

        assertEquals(0, response.getCancelledOccurrences());
        assertEquals(List.of(), response.getCancelledBookingIds());
        assertEquals(BookingSeriesStatus.CANCELLED, series.getStatus());
        verify(bookingRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("Rejects cancellation when the series is already cancelled")
    void cancelAlreadyCancelledSeries() {
        BookingSeries series = series(customer(), BookingSeriesStatus.CANCELLED);
        when(bookingSeriesRepository.findByIdForUpdate(SERIES_ID)).thenReturn(Optional.of(series));

        assertThrows(ConflictException.class, () -> bookingSeriesService.cancelSeries(SERIES_ID));

        verifyNoInteractions(bookingRepository, availabilityRepository, paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Reports a missing series when cancellation is requested for an unknown ID")
    void cancelSeriesMissing() {
        when(bookingSeriesRepository.findByIdForUpdate(SERIES_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingSeriesService.cancelSeries(SERIES_ID));

        verifyNoInteractions(bookingRepository, availabilityRepository, paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Rejects series cancellation if an active booking has no corresponding availability slot")
    void cancelSeriesMissingSlot() {
        BookingSeries series = series(customer(), BookingSeriesStatus.ACTIVE);
        Booking active = booking(101L, BookingStatus.CONFIRMED, START_DATE);
        when(bookingSeriesRepository.findByIdForUpdate(SERIES_ID)).thenReturn(Optional.of(series));
        when(bookingRepository.findByBookingSeries_IdOrderByBookingDateAsc(SERIES_ID)).thenReturn(List.of(active));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(21L, START_DATE, START_TIME))
                .thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> bookingSeriesService.cancelSeries(SERIES_ID));

        verify(bookingRepository, never()).saveAll(any());
        verifyNoInteractions(paymentRecordService, eventPublisherService);
    }

    private BookingSeriesRequest request(int count, LocalTime start, LocalTime end) {
        BookingSeriesRequest request = new BookingSeriesRequest();
        request.setCustomerId(CUSTOMER_ID);
        request.setLocality("Central");
        request.setSkill(com.househelper.model.SkillType.CLEANING);
        request.setStartDate(START_DATE);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setPaymentMethod(com.househelper.model.PaymentMethod.CARD);
        request.setOccurrenceCount(count);
        return request;
    }

    private Customer customer() {
        return Customer.builder().id(CUSTOMER_ID).name("Customer").address("Address").build();
    }

    private BookingSeries series(Customer customer, BookingSeriesStatus status) {
        return BookingSeries.builder()
                .id(SERIES_ID)
                .customer(customer)
                .startDate(START_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .occurrenceCount(3)
                .status(status)
                .build();
    }

    private BookingResponse bookingResponse(Long bookingId, Long seriesId) {
        return BookingResponse.builder().id(bookingId).seriesId(seriesId).build();
    }

    private Booking booking(Long id, BookingStatus status, LocalDate date) {
        return Booking.builder()
                .id(id)
                .customer(customer())
                .bookingSeries(series(customer(), BookingSeriesStatus.ACTIVE))
                .assignedHelperId(21L)
                .locality("Central")
                .skill(com.househelper.model.SkillType.CLEANING)
                .bookingDate(date)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .totalAmount(100.0)
                .status(status)
                .build();
    }

    private HelperAvailability slot(Long id) {
        return HelperAvailability.builder()
                .id(id)
                .slotDate(START_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .status(AvailabilityStatus.BOOKED)
                .build();
    }
}
