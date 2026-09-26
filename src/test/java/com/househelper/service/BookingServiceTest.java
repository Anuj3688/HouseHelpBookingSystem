package com.househelper.service;

import com.househelper.dto.BookingRequest;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.RescheduleRequest;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.exception.SlotUnavailableException;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Booking;
import com.househelper.model.BookingSeries;
import com.househelper.model.BookingStatus;
import com.househelper.model.Customer;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentType;
import com.househelper.model.SkillType;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Long CUSTOMER_ID = 11L;
    private static final Long HELPER_ID = 22L;
    private static final Long BOOKING_ID = 33L;
    private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 10, 5);
    private static final LocalTime START_TIME = LocalTime.of(9, 0);
    private static final LocalTime END_TIME = LocalTime.of(10, 0);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private HelperAvailabilityRepository availabilityRepository;

    @Mock
    private PaymentRecordService paymentRecordService;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        bookingService = new BookingService(bookingRepository, customerRepository, availabilityRepository,
                paymentRecordService, eventPublisherService, transactionManager);
    }

    @Test
    @DisplayName("Creates a pending booking, reserves its slot, and starts a method-specific payment")
    void createBooking() {
        Customer customer = customer();
        HelperAvailability slot = slot(51L, helper(350.0), BOOKING_DATE, START_TIME, END_TIME);
        Payment payment = payment(71L, BOOKING_ID, null, PaymentType.BOOKING_PAYMENT);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(availabilityRepository.findAvailableHelpersForSlot(
                "Central", SkillType.CLEANING, BOOKING_DATE, START_TIME, END_TIME, AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(slot));
        when(availabilityRepository.findById(51L)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            saved.setId(BOOKING_ID);
            return saved;
        });
        when(paymentRecordService.createBookingPayment(
                eq(BOOKING_ID), isNull(),
                argThat(amount -> amount.compareTo(BigDecimal.valueOf(350.0)) == 0),
                eq(PaymentMethod.UPI))).thenReturn(payment);

        BookingResponse response = bookingService.createBooking(request(PaymentMethod.UPI));

        assertEquals(BOOKING_ID, response.getId());
        assertEquals(CUSTOMER_ID, response.getCustomerId());
        assertEquals(HELPER_ID, response.getAssignedHelperId());
        assertEquals(350.0, response.getTotalAmount());
        assertEquals(BookingStatus.PENDING_PAYMENT, response.getStatus());
        assertEquals(71L, response.getPaymentId());
        assertEquals(AvailabilityStatus.BOOKED, slot.getStatus());
        verify(paymentRecordService).createBookingPayment(
                eq(BOOKING_ID), isNull(),
                argThat(amount -> amount.compareTo(BigDecimal.valueOf(350.0)) == 0),
                eq(PaymentMethod.UPI));
        verify(eventPublisherService).publishEvent(
                eq("BOOKING_CREATED"), eq("Booking"), eq(BOOKING_ID.toString()),
                eq(HELPER_ID), eq(CUSTOMER_ID), eq(71L), eq(BOOKING_ID),
                isNull(), any(BookingResponse.class));
    }

    @Test
    @DisplayName("Links a recurring occurrence to its series and creates an independent payment")
    void createRecurringOccurrence() {
        BookingSeries series = BookingSeries.builder().id(88L).customer(customer()).build();
        Customer customer = customer();
        HelperAvailability slot = slot(51L, helper(200.0), BOOKING_DATE, START_TIME, END_TIME);
        Payment payment = payment(71L, BOOKING_ID, 88L, PaymentType.BOOKING_PAYMENT);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(availabilityRepository.findAvailableHelpersForSlot(
                "Central", SkillType.CLEANING, BOOKING_DATE, START_TIME, END_TIME, AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(slot));
        when(availabilityRepository.findById(51L)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            saved.setId(BOOKING_ID);
            return saved;
        });
        when(paymentRecordService.createBookingPayment(
                eq(BOOKING_ID), eq(88L),
                argThat(amount -> amount.compareTo(BigDecimal.valueOf(200.0)) == 0),
                eq(PaymentMethod.CARD))).thenReturn(payment);

        BookingResponse response = bookingService.createRecurringBookingOccurrence(series, request(PaymentMethod.CARD));

        assertEquals(88L, response.getSeriesId());
        assertEquals(71L, response.getPaymentId());
        assertEquals(BookingStatus.PENDING_PAYMENT, response.getStatus());
        verify(paymentRecordService).createBookingPayment(
                eq(BOOKING_ID), eq(88L),
                argThat(amount -> amount.compareTo(BigDecimal.valueOf(200.0)) == 0),
                eq(PaymentMethod.CARD));
    }

    @Test
    @DisplayName("Rejects a booking when the customer does not exist")
    void createBookingCustomerMissing() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(request(PaymentMethod.CARD)));

        verifyNoInteractions(availabilityRepository, paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Rejects a booking when no helper has the requested slot")
    void createBookingSlotUnavailable() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(availabilityRepository.findAvailableHelpersForSlot(
                "Central", SkillType.CLEANING, BOOKING_DATE, START_TIME, END_TIME, AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of());

        assertThrows(SlotUnavailableException.class,
                () -> bookingService.createBooking(request(PaymentMethod.CARD)));

        verifyNoInteractions(paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Rejects a booking whose end time is not after its start time")
    void createBookingInvalidPeriod() {
        BookingRequest request = request(PaymentMethod.CARD);
        request.setEndTime(START_TIME);

        assertThrows(InvalidRequestException.class, () -> bookingService.createBooking(request));

        verifyNoInteractions(bookingRepository, customerRepository, availabilityRepository,
                paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Confirms a pending booking after its payment succeeds")
    void confirmAfterPaymentSuccess() {
        Booking booking = booking(BookingStatus.PENDING_PAYMENT, null);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        bookingService.confirmBookingAfterPaymentSuccess(BOOKING_ID, 71L);

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        verify(bookingRepository).save(booking);
        verify(eventPublisherService).publishEvent(
                eq("BOOKING_CONFIRMED_AFTER_PAYMENT"), eq("Booking"), eq(BOOKING_ID.toString()),
                eq(HELPER_ID), eq(CUSTOMER_ID), eq(71L), eq(BOOKING_ID), isNull(),
                any(BookingResponse.class));
    }

    @Test
    @DisplayName("Does not change a booking that is no longer pending when payment succeeds")
    void confirmAlreadyCancelled() {
        Booking booking = booking(BookingStatus.CANCELLED, null);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(paymentRecordService.createCancellationRefund(BOOKING_ID, null, null))
                .thenReturn(Optional.empty());

        bookingService.confirmBookingAfterPaymentSuccess(BOOKING_ID, 71L);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(bookingRepository, never()).save(booking);
        verify(paymentRecordService).createCancellationRefund(BOOKING_ID, null, null);
        verify(eventPublisherService).publishEvent(
                eq("PAYMENT_SUCCEEDED_AFTER_BOOKING_CANCELLATION"), eq("Booking"), eq(BOOKING_ID.toString()),
                eq(HELPER_ID), eq(CUSTOMER_ID), eq(71L), eq(BOOKING_ID), isNull(), any());
    }

    @Test
    @DisplayName("Cancels a booking after payment failure, releases its slot, and records the audit event")
    void cancelAfterPaymentFailure() {
        Booking booking = booking(BookingStatus.PENDING_PAYMENT, null);
        HelperAvailability slot = slot(51L, helper(350.0), BOOKING_DATE, START_TIME, END_TIME);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(HELPER_ID, BOOKING_DATE, START_TIME))
                .thenReturn(Optional.of(slot));
        when(paymentRecordService.createCancellationRefund(BOOKING_ID, null, 71L))
                .thenReturn(Optional.empty());

        bookingService.cancelBookingAfterPaymentFailure(BOOKING_ID, 71L);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(AvailabilityStatus.AVAILABLE, slot.getStatus());
        verify(bookingRepository).save(booking);
        verify(paymentRecordService).createCancellationRefund(BOOKING_ID, null, 71L);
        verify(eventPublisherService).publishEvent(
                eq("BOOKING_CANCELLED_AFTER_PAYMENT_FAILURE"), eq("Booking"), eq(BOOKING_ID.toString()),
                eq(HELPER_ID), eq(CUSTOMER_ID), eq(71L), eq(BOOKING_ID), isNull(), any());
    }

    @Test
    @DisplayName("Releases a booking slot, records a refund request, and returns the cancelled booking")
    void cancelBooking() {
        Booking booking = booking(BookingStatus.CONFIRMED, null);
        HelperAvailability slot = slot(51L, helper(350.0), BOOKING_DATE, START_TIME, END_TIME);
        Payment refund = payment(72L, BOOKING_ID, null, PaymentType.CANCEL_REFUND);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(HELPER_ID, BOOKING_DATE, START_TIME))
                .thenReturn(Optional.of(slot));
        when(paymentRecordService.findPaymentsForBooking(BOOKING_ID)).thenReturn(List.of());
        when(paymentRecordService.createCancellationRefund(BOOKING_ID, null, null))
                .thenReturn(Optional.of(refund));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.cancelBooking(BOOKING_ID);

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        assertEquals(AvailabilityStatus.AVAILABLE, slot.getStatus());
        verify(bookingRepository).save(booking);
        verify(eventPublisherService).publishEvent(
                eq("BOOKING_CANCELLED"), eq("Booking"), eq(BOOKING_ID.toString()),
                eq(HELPER_ID), eq(CUSTOMER_ID), isNull(), eq(BOOKING_ID), isNull(), any());
    }

    @Test
    @DisplayName("Rejects a second cancellation request for an already cancelled booking")
    void cancelAlreadyCancelled() {
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(BookingStatus.CANCELLED, null)));

        assertThrows(ConflictException.class, () -> bookingService.cancelBooking(BOOKING_ID));

        verifyNoInteractions(availabilityRepository, paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Reschedules a booking, releases the old slot, and records the price increase")
    void rescheduleBooking() {
        Booking booking = booking(BookingStatus.CONFIRMED, null);
        HelperAvailability oldSlot = slot(51L, helper(100.0), BOOKING_DATE, START_TIME, END_TIME);
        LocalDate newDate = BOOKING_DATE.plusDays(1);
        LocalTime newStart = LocalTime.of(10, 0);
        LocalTime newEnd = LocalTime.of(11, 0);
        HelperAvailability newSlot = slot(52L, helper(11L, 150.0), newDate, newStart, newEnd);
        Payment adjustment = payment(73L, BOOKING_ID, null, PaymentType.RESCHEDULE_PAYMENT);
        RescheduleRequest request = new RescheduleRequest(newDate, newStart, newEnd);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findAvailableHelpersForSlot(
                "Central", SkillType.CLEANING, newDate, newStart, newEnd, AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(newSlot));
        when(availabilityRepository.findById(52L)).thenReturn(Optional.of(newSlot));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(HELPER_ID, BOOKING_DATE, START_TIME))
                .thenReturn(Optional.of(oldSlot));
        when(paymentRecordService.createRescheduleAdjustment(
                eq(BOOKING_ID), isNull(),
                argThat(amount -> amount.compareTo(BigDecimal.valueOf(50.0)) == 0))).thenReturn(adjustment);
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.rescheduleBooking(BOOKING_ID, request);

        assertEquals(BookingStatus.RESCHEDULED, response.getStatus());
        assertEquals(newDate, response.getBookingDate());
        assertEquals(11L, response.getAssignedHelperId());
        assertEquals(150.0, response.getTotalAmount());
        assertEquals(AvailabilityStatus.AVAILABLE, oldSlot.getStatus());
        assertEquals(AvailabilityStatus.BOOKED, newSlot.getStatus());
        assertEquals(73L, response.getPaymentId());
        verify(paymentRecordService).createRescheduleAdjustment(
                eq(BOOKING_ID), isNull(),
                argThat(amount -> amount.compareTo(BigDecimal.valueOf(50.0)) == 0));
    }

    @Test
    @DisplayName("Rejects rescheduling a booking while its payment is pending")
    void reschedulePendingPayment() {
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(BookingStatus.PENDING_PAYMENT, null)));

        assertThrows(ConflictException.class, () -> bookingService.rescheduleBooking(
                BOOKING_ID, new RescheduleRequest(BOOKING_DATE.plusDays(1), START_TIME, END_TIME)));

        verifyNoInteractions(availabilityRepository, paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Rejects rescheduling to the booking's current date and time")
    void rescheduleSamePeriod() {
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, null)));

        assertThrows(InvalidRequestException.class, () -> bookingService.rescheduleBooking(
                BOOKING_ID, new RescheduleRequest(BOOKING_DATE, START_TIME, END_TIME)));

        verifyNoInteractions(availabilityRepository, paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Rejects a reschedule request when no helper is available for the new slot")
    void rescheduleSlotUnavailable() {
        LocalDate newDate = BOOKING_DATE.plusDays(1);
        RescheduleRequest request = new RescheduleRequest(newDate, START_TIME, END_TIME);
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, null)));
        when(availabilityRepository.findAvailableHelpersForSlot(
                "Central", SkillType.CLEANING, newDate, START_TIME, END_TIME, AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of());

        assertThrows(SlotUnavailableException.class,
                () -> bookingService.rescheduleBooking(BOOKING_ID, request));

        verifyNoInteractions(paymentRecordService, eventPublisherService);
    }

    @Test
    @DisplayName("Reports a missing booking when attempting to confirm payment")
    void confirmBookingMissing() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.confirmBookingAfterPaymentSuccess(BOOKING_ID, 71L));

        verifyNoInteractions(availabilityRepository, paymentRecordService, eventPublisherService);
    }

    private BookingRequest request(PaymentMethod paymentMethod) {
        return BookingRequest.builder()
                .customerId(CUSTOMER_ID)
                .locality(" Central ")
                .skill(SkillType.CLEANING)
                .bookingDate(BOOKING_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .paymentMethod(paymentMethod)
                .build();
    }

    private Customer customer() {
        return Customer.builder().id(CUSTOMER_ID).name("Customer").address("Address").build();
    }

    private Helper helper(Double hourlyRate) {
        return helper(HELPER_ID, hourlyRate);
    }

    private Helper helper(Long id, Double hourlyRate) {
        return Helper.builder()
                .id(id)
                .name("Helper")
                .phone("5550101")
                .hourlyRate(hourlyRate)
                .build();
    }

    private HelperAvailability slot(Long id, Helper helper, LocalDate date, LocalTime start, LocalTime end) {
        return HelperAvailability.builder()
                .id(id)
                .helper(helper)
                .slotDate(date)
                .startTime(start)
                .endTime(end)
                .status(AvailabilityStatus.AVAILABLE)
                .build();
    }

    private Booking booking(BookingStatus status, BookingSeries series) {
        return Booking.builder()
                .id(BOOKING_ID)
                .customer(customer())
                .bookingSeries(series)
                .assignedHelperId(HELPER_ID)
                .locality("Central")
                .skill(SkillType.CLEANING)
                .bookingDate(BOOKING_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .totalAmount(100.0)
                .status(status)
                .build();
    }

    private Payment payment(Long id, Long bookingId, Long seriesId, PaymentType paymentType) {
        return Payment.builder()
                .id(id)
                .bookingId(bookingId)
                .bookingSeriesId(seriesId)
                .paymentType(paymentType)
                .amount(100.0)
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }
}
