package com.househelper.service.booking;

import com.househelper.dto.BookingRequest;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.BookingSeriesRequest;
import com.househelper.dto.BookingSeriesResponse;
import com.househelper.dto.InstantBookingRequest;
import com.househelper.dto.UnifiedBookingRequest;
import com.househelper.exception.InvalidRequestException;
import com.househelper.model.BookingStatus;
import com.househelper.model.BookingType;
import com.househelper.model.PaymentMethod;
import com.househelper.model.SkillType;
import com.househelper.service.BookingSeriesService;
import com.househelper.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingStrategyRegistryTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID HELPER_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID SERIES_ID = UUID.randomUUID();
    private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 10, 10);
    private static final LocalTime START_TIME = LocalTime.of(9, 0);
    private static final LocalTime END_TIME = LocalTime.of(10, 0);

    @Mock
    private BookingService bookingService;

    @Mock
    private BookingSeriesService bookingSeriesService;

    private ScheduledBookingStrategy scheduledBookingStrategy;
    private InstantBookingStrategy instantBookingStrategy;
    private RecurringBookingStrategy recurringBookingStrategy;
    private BookingStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        scheduledBookingStrategy = new ScheduledBookingStrategy(bookingService);
        instantBookingStrategy = new InstantBookingStrategy(bookingService);
        recurringBookingStrategy = new RecurringBookingStrategy(bookingSeriesService);

        registry = new BookingStrategyRegistry(List.of(
                scheduledBookingStrategy,
                instantBookingStrategy,
                recurringBookingStrategy
        ));
    }

    @Test
    @DisplayName("Registers all strategies and retrieves them by booking type")
    void registryDiscoversAllStrategies() {
        assertEquals(scheduledBookingStrategy, registry.getStrategy(BookingType.SCHEDULED));
        assertEquals(instantBookingStrategy, registry.getStrategy(BookingType.INSTANT));
        assertEquals(recurringBookingStrategy, registry.getStrategy(BookingType.RECURRING));
        assertTrue(registry.supports(BookingType.SCHEDULED));
        assertTrue(registry.supports(BookingType.INSTANT));
        assertTrue(registry.supports(BookingType.RECURRING));
    }

    @Test
    @DisplayName("Throws InvalidRequestException when queried with null or unsupported type")
    void registryRejectsInvalidType() {
        assertThrows(InvalidRequestException.class, () -> registry.getStrategy(null));
    }

    @Test
    @DisplayName("Scheduled strategy executes BookingRequest and wraps in BookingResult")
    void scheduledStrategyExecutesBookingRequest() {
        BookingRequest request = BookingRequest.builder()
                .customerId(CUSTOMER_ID)
                .locality("Koramangala")
                .skill(SkillType.CLEANING)
                .bookingDate(BOOKING_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .paymentMethod(PaymentMethod.CARD)
                .build();

        BookingResponse mockResponse = BookingResponse.builder()
                .id(BOOKING_ID)
                .customerId(CUSTOMER_ID)
                .assignedHelperId(HELPER_ID)
                .bookingType(BookingType.SCHEDULED)
                .status(BookingStatus.PENDING_PAYMENT)
                .build();

        when(bookingService.createBooking(request)).thenReturn(mockResponse);

        BookingResult result = registry.getStrategy(BookingType.SCHEDULED).execute(request);

        assertNotNull(result);
        assertEquals(BookingType.SCHEDULED, result.getBookingType());
        assertEquals(BOOKING_ID, result.getPrimaryBooking().getId());
        verify(bookingService).createBooking(request);
    }

    @Test
    @DisplayName("Scheduled strategy handles polymorphic UnifiedBookingRequest")
    void scheduledStrategyExecutesUnifiedRequest() {
        UnifiedBookingRequest unified = UnifiedBookingRequest.builder()
                .bookingType(BookingType.SCHEDULED)
                .customerId(CUSTOMER_ID)
                .locality("Indiranagar")
                .skill(SkillType.COOKING)
                .bookingDate(BOOKING_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        BookingResponse mockResponse = BookingResponse.builder()
                .id(BOOKING_ID)
                .bookingType(BookingType.SCHEDULED)
                .build();

        when(bookingService.createBooking(any(BookingRequest.class))).thenReturn(mockResponse);

        BookingResult result = registry.getStrategy(BookingType.SCHEDULED).execute(unified);

        assertEquals(BookingType.SCHEDULED, result.getBookingType());
        assertEquals(BOOKING_ID, result.getPrimaryBooking().getId());
    }

    @Test
    @DisplayName("Instant strategy executes InstantBookingRequest and returns BookingResult")
    void instantStrategyExecutesInstantRequest() {
        InstantBookingRequest request = new InstantBookingRequest();
        request.setCustomerId(CUSTOMER_ID);
        request.setLocality("Whitefield");
        request.setSkill(SkillType.CLEANING);
        request.setPaymentMethod(PaymentMethod.WALLET);

        BookingResponse mockResponse = BookingResponse.builder()
                .id(BOOKING_ID)
                .bookingType(BookingType.INSTANT)
                .build();

        when(bookingService.createInstantBooking(request)).thenReturn(mockResponse);

        BookingResult result = registry.getStrategy(BookingType.INSTANT).execute(request);

        assertNotNull(result);
        assertEquals(BookingType.INSTANT, result.getBookingType());
        assertEquals(BOOKING_ID, result.getPrimaryBooking().getId());
        verify(bookingService).createInstantBooking(request);
    }

    @Test
    @DisplayName("Recurring strategy executes BookingSeriesRequest and returns series BookingResult")
    void recurringStrategyExecutesSeriesRequest() {
        BookingSeriesRequest request = new BookingSeriesRequest();
        request.setCustomerId(CUSTOMER_ID);
        request.setLocality("HSR Layout");
        request.setSkill(SkillType.CLEANING);
        request.setStartDate(BOOKING_DATE);
        request.setStartTime(START_TIME);
        request.setEndTime(END_TIME);
        request.setOccurrenceCount(4);
        request.setRecurrenceDays(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        request.setPaymentMethod(PaymentMethod.CARD);

        BookingResponse b1 = BookingResponse.builder().id(UUID.randomUUID()).seriesId(SERIES_ID).build();
        BookingResponse b2 = BookingResponse.builder().id(UUID.randomUUID()).seriesId(SERIES_ID).build();

        BookingSeriesResponse seriesResponse = BookingSeriesResponse.builder()
                .seriesId(SERIES_ID)
                .requestedOccurrences(4)
                .createdBookings(List.of(b1, b2))
                .unavailableDates(List.of(BOOKING_DATE.plusWeeks(2)))
                .recurrenceDays(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
                .build();

        when(bookingSeriesService.createWeeklySeries(request)).thenReturn(seriesResponse);

        BookingResult result = registry.getStrategy(BookingType.RECURRING).execute(request);

        assertNotNull(result);
        assertEquals(BookingType.RECURRING, result.getBookingType());
        assertEquals(SERIES_ID, result.getSeriesId());
        assertEquals(2, result.getBookings().size());
        assertEquals(1, result.getUnavailableDates().size());
        assertEquals(4, result.getRequestedOccurrences());
        verify(bookingSeriesService).createWeeklySeries(request);
    }

    @Test
    @DisplayName("Demonstrates Extensibility: new custom booking type can be plugged in without modifying existing code")
    void demonstratesExtensibilityWithCustomStrategy() {
        // Mock custom booking type and strategy (e.g. specialized VIP or Emergency booking)
        BookingTypeStrategy customStrategy = new BookingTypeStrategy() {
            @Override
            public BookingType bookingType() {
                return BookingType.SCHEDULED; // Reusing enum for custom handler demonstration
            }

            @Override
            public BookingResult execute(BookingCommand command) {
                return BookingResult.builder()
                        .bookingType(BookingType.SCHEDULED)
                        .bookings(List.of(BookingResponse.builder().id(BOOKING_ID).build()))
                        .build();
            }
        };

        BookingStrategyRegistry customRegistry = new BookingStrategyRegistry(List.of(customStrategy));
        assertTrue(customRegistry.supports(BookingType.SCHEDULED));
        assertEquals(BOOKING_ID, customRegistry.getStrategy(BookingType.SCHEDULED)
                .execute(new InstantBookingRequest())
                .getPrimaryBooking()
                .getId());
    }

    @Test
    @DisplayName("Rejects UnifiedBookingRequest when required scheduled fields are missing")
    void rejectsScheduledUnifiedWithoutDate() {
        UnifiedBookingRequest unified = UnifiedBookingRequest.builder()
                .bookingType(BookingType.SCHEDULED)
                .customerId(CUSTOMER_ID)
                .locality("Indiranagar")
                .skill(SkillType.COOKING)
                .paymentMethod(PaymentMethod.UPI)
                .build(); // Missing bookingDate, startTime, endTime

        assertThrows(InvalidRequestException.class,
                () -> registry.getStrategy(BookingType.SCHEDULED).execute(unified));
    }

    @Test
    @DisplayName("Rejects UnifiedBookingRequest when required recurring fields are missing")
    void rejectsRecurringUnifiedWithoutOccurrenceCount() {
        UnifiedBookingRequest unified = UnifiedBookingRequest.builder()
                .bookingType(BookingType.RECURRING)
                .customerId(CUSTOMER_ID)
                .locality("Indiranagar")
                .skill(SkillType.COOKING)
                .bookingDate(BOOKING_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .paymentMethod(PaymentMethod.UPI)
                .build(); // Missing occurrenceCount

        assertThrows(InvalidRequestException.class,
                () -> registry.getStrategy(BookingType.RECURRING).execute(unified));
    }
}
