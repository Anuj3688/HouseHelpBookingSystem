package com.househelper.service;

import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Booking;
import com.househelper.model.BookingSeries;
import com.househelper.model.BookingStatus;
import com.househelper.model.Customer;
import com.househelper.model.Gender;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentStatus;
import com.househelper.model.PaymentType;
import com.househelper.model.SkillType;
import com.househelper.model.SystemEvent;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import com.househelper.repository.HelperRepository;
import com.househelper.repository.PaymentRepository;
import com.househelper.repository.SystemEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HousekeepingServiceTest {

    @Mock
    private HelperAvailabilityRepository availabilityRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SystemEventRepository systemEventRepository;

    @InjectMocks
    private HousekeepingService housekeepingService;

    @Test
    @DisplayName("Returns unique helpers with available slots even when a helper has multiple slots")
    void getAvailableHelpers() {
        Helper helper = helper(21L);
        when(availabilityRepository.findAllSlotsByStatusWithHelper(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(slot(31L, helper), slot(32L, helper)));

        var results = housekeepingService.getAvailableHelpers();

        assertEquals(1, results.size());
        assertEquals(21L, results.getFirst().getId());
        assertEquals(4.5, results.getFirst().getRating());
    }

    @Test
    @DisplayName("Returns registered helpers including their profile and rating information")
    void getRegisteredHelpers() {
        when(helperRepository.findAll()).thenReturn(List.of(helper(21L)));

        var results = housekeepingService.getRegisteredHelpers();

        assertEquals(1, results.size());
        assertEquals("Helper 21", results.getFirst().getName());
        assertEquals(Set.of("Central"), results.getFirst().getLocalities());
    }

    @Test
    @DisplayName("Returns registered customers as customer response records")
    void getRegisteredCustomers() {
        when(customerRepository.findAll()).thenReturn(List.of(
                Customer.builder().id(11L).name("Customer").address("Address").build()));

        var results = housekeepingService.getRegisteredCustomers();

        assertEquals(11L, results.getFirst().getId());
        assertEquals("Customer", results.getFirst().getName());
    }

    @Test
    @DisplayName("Returns available slots with helper and rate details")
    void getAvailableSlots() {
        when(availabilityRepository.findAllSlotsByStatusWithHelper(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(slot(31L, helper(21L))));

        var results = housekeepingService.getAvailableSlots();

        assertEquals(1, results.size());
        assertEquals(31L, results.getFirst().getId());
        assertEquals(21L, results.getFirst().getHelperId());
        assertEquals("Helper 21", results.getFirst().getHelperName());
        assertEquals(350.0, results.getFirst().getHourlyRate());
    }

    @Test
    @DisplayName("Returns bookings with recurring series ID and initial payment ID")
    void getAllBookings() {
        Customer customer = Customer.builder().id(11L).name("Customer").address("Address").build();
        Booking booking = Booking.builder()
                .id(41L)
                .customer(customer)
                .bookingSeries(BookingSeries.builder().id(51L).build())
                .assignedHelperId(21L)
                .locality("Central")
                .skill(SkillType.CLEANING)
                .bookingDate(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .totalAmount(350.0)
                .status(BookingStatus.CONFIRMED)
                .build();
        Payment initial = payment(61L, 41L, PaymentType.BOOKING_PAYMENT);
        Payment adjustment = payment(62L, 41L, PaymentType.RESCHEDULE_PAYMENT);
        when(bookingRepository.findAll()).thenReturn(List.of(booking));
        when(paymentRepository.findByBookingIdIn(List.of(41L))).thenReturn(List.of(initial, adjustment));

        var results = housekeepingService.getAllBookings();

        assertEquals(1, results.size());
        assertEquals(51L, results.getFirst().getSeriesId());
        assertEquals(61L, results.getFirst().getPaymentId());
        assertEquals(11L, results.getFirst().getCustomerId());
    }

    @Test
    @DisplayName("Returns all payment records with provider reference and typed payment method")
    void getAllPayments() {
        Payment payment = payment(61L, 41L, PaymentType.BOOKING_PAYMENT);
        payment.setProviderReference("CARD-REF-61");
        when(paymentRepository.findAll()).thenReturn(List.of(payment));

        var results = housekeepingService.getAllPayments();

        assertEquals(1, results.size());
        assertEquals("CARD-REF-61", results.getFirst().getProviderReference());
        assertEquals(PaymentMethod.CARD, results.getFirst().getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, results.getFirst().getPaymentStatus());
    }

    @Test
    @DisplayName("Passes all supplied identifiers to the event repository for filtered audit lookup")
    void getAllEvents() {
        List<SystemEvent> events = List.of(SystemEvent.builder().eventType("BOOKING_CREATED").build());
        when(systemEventRepository.findAllFiltered(1L, 2L, 3L, 4L, 5L)).thenReturn(events);

        assertEquals(events, housekeepingService.getAllEvents(1L, 2L, 3L, 4L, 5L));

        verify(systemEventRepository).findAllFiltered(1L, 2L, 3L, 4L, 5L);
    }

    private Helper helper(Long id) {
        return Helper.builder()
                .id(id)
                .name("Helper " + id)
                .gender(Gender.FEMALE)
                .localities(Set.of("Central"))
                .skills(Set.of(SkillType.CLEANING))
                .hourlyRate(350.0)
                .totalRating(BigDecimal.valueOf(9))
                .ratingCount(2L)
                .build();
    }

    private HelperAvailability slot(Long id, Helper helper) {
        return HelperAvailability.builder()
                .id(id)
                .helper(helper)
                .slotDate(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status(AvailabilityStatus.AVAILABLE)
                .build();
    }

    private Payment payment(Long id, Long bookingId, PaymentType type) {
        return Payment.builder()
                .id(id)
                .bookingId(bookingId)
                .paymentType(type)
                .amount(350.0)
                .paymentMethod(PaymentMethod.CARD)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
    }
}
