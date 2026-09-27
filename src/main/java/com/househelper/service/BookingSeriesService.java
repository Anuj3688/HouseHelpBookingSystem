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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.Clock;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingSeriesService {

    private final BookingSeriesRepository bookingSeriesRepository;
    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final HelperAvailabilityRepository availabilityRepository;
    private final BookingService bookingService;
    private final PaymentRecordService paymentRecordService;
    private final EventPublisherService eventPublisherService;
    private final Clock clock;

    public BookingSeriesResponse createWeeklySeries(BookingSeriesRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidRequestException("End time must be later than start time.");
        }
        if (!LocalDateTime.of(request.getStartDate(), request.getStartTime())
                .isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("Recurring bookings must start in the future.");
        }
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer " + request.getCustomerId() + " was not found."));
        Set<DayOfWeek> recurrenceDays = request.getRecurrenceDays() == null
                || request.getRecurrenceDays().isEmpty()
                ? Set.of(request.getStartDate().getDayOfWeek())
                : EnumSet.copyOf(request.getRecurrenceDays());
        BookingSeries series = bookingSeriesRepository.save(BookingSeries.builder()
                .customer(customer)
                .startDate(request.getStartDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .occurrenceCount(request.getOccurrenceCount())
                .recurrenceDays(recurrenceDays)
                .status(BookingSeriesStatus.ACTIVE)
                .build());

        List<BookingResponse> createdBookings = new ArrayList<>();
        List<LocalDate> unavailableDates = new ArrayList<>();
        for (LocalDate date : occurrenceDates(request.getStartDate(), recurrenceDays, request.getOccurrenceCount())) {
            try {
                createdBookings.add(bookingService.createRecurringBookingOccurrence(series,
                        toBookingRequest(request, date)));
            } catch (SlotUnavailableException exception) {
                unavailableDates.add(date);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seriesId", series.getId());
        payload.put("requestedOccurrences", request.getOccurrenceCount());
        payload.put("recurrenceDays", recurrenceDays);
        payload.put("createdBookingIds", createdBookings.stream().map(BookingResponse::getId).toList());
        payload.put("unavailableDates", unavailableDates);
        eventPublisherService.publishEvent("BOOKING_SERIES_CREATED", "BookingSeries",
                series.getId().toString(), null, customer.getId(), null, null, series.getId(), payload);

        return BookingSeriesResponse.builder()
                .seriesId(series.getId())
                .requestedOccurrences(request.getOccurrenceCount())
                .createdBookings(createdBookings)
                .unavailableDates(unavailableDates)
                .recurrenceDays(recurrenceDays)
                .build();
    }

    @Transactional
    public BookingSeriesCancellationResponse cancelSeries(UUID seriesId) {
        BookingSeries series = bookingSeriesRepository.findByIdForUpdate(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking series " + seriesId + " was not found."));
        if (series.getStatus() == BookingSeriesStatus.CANCELLED) {
            throw new ConflictException("Booking series " + seriesId + " is already cancelled.");
        }

        List<Booking> bookings = bookingRepository.findByBookingSeries_IdOrderByBookingDateAsc(seriesId);
        List<Booking> activeBookings = bookings.stream()
                .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
                .toList();
        activeBookings.forEach(booking -> {
            HelperAvailability slot = availabilityRepository.findByHelperIdAndSlotDateAndStartTime(
                            booking.getAssignedHelperId(), booking.getBookingDate(), booking.getStartTime())
                    .orElseThrow(() -> new ConflictException(
                            "The availability slot for booking " + booking.getId() + " was not found."));
            slot.setStatus(AvailabilityStatus.AVAILABLE);
            booking.setStatus(BookingStatus.CANCELLED);
        });
        bookingRepository.saveAll(activeBookings);
        series.setStatus(BookingSeriesStatus.CANCELLED);

        List<UUID> bookingIds = activeBookings.stream().map(Booking::getId).toList();
        Optional<Payment> refund = paymentRecordService.createSeriesCancellationRefund(seriesId, bookingIds);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seriesId", seriesId);
        payload.put("cancelledBookingIds", bookingIds);
        payload.put("refundPaymentId", refund.map(Payment::getId).orElse(null));
        eventPublisherService.publishEvent("BOOKING_SERIES_CANCELLED", "BookingSeries",
                seriesId.toString(), null, series.getCustomer().getId(),
                refund.map(Payment::getId).orElse(null), null, seriesId, payload);

        return BookingSeriesCancellationResponse.builder()
                .seriesId(seriesId)
                .cancelledOccurrences(activeBookings.size())
                .cancelledBookingIds(bookingIds)
                .refundPaymentId(refund.map(Payment::getId).orElse(null))
                .build();
    }

    private BookingRequest toBookingRequest(BookingSeriesRequest seriesRequest, LocalDate bookingDate) {
        BookingRequest request = new BookingRequest();
        request.setCustomerId(seriesRequest.getCustomerId());
        request.setLocality(seriesRequest.getLocality());
        request.setSkill(seriesRequest.getSkill());
        request.setBookingDate(bookingDate);
        request.setStartTime(seriesRequest.getStartTime());
        request.setEndTime(seriesRequest.getEndTime());
        request.setPaymentMethod(seriesRequest.getPaymentMethod());
        return request;
    }

    private List<LocalDate> occurrenceDates(LocalDate startDate, Set<DayOfWeek> recurrenceDays,
                                            int occurrenceCount) {
        List<LocalDate> dates = new ArrayList<>(occurrenceCount);
        for (int daysFromStart = 0; dates.size() < occurrenceCount && daysFromStart <= 364; daysFromStart++) {
            LocalDate date = startDate.plusDays(daysFromStart);
            if (recurrenceDays.contains(date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        if (dates.size() != occurrenceCount) {
            throw new InvalidRequestException("The recurrence pattern does not produce enough dates within one year.");
        }
        return dates;
    }
}
