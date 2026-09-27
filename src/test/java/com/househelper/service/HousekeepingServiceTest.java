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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HousekeepingServiceTest {

    private static UUID uuid(long value) {
        return UUID.nameUUIDFromBytes(("test-id-" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

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
        Helper helper = helper(uuid(21));
        when(availabilityRepository.findAllSlotsByStatusWithHelper(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(slot(uuid(31), helper), slot(uuid(32), helper)));

        var results = housekeepingService.getAvailableHelpers();

        assertEquals(1, results.size());
        assertEquals(uuid(21), results.getFirst().getId());
        assertEquals(4.5, results.getFirst().getRating());
    }

    @Test
    @DisplayName("Returns registered helpers including their profile and rating information")
    void getRegisteredHelpers() {
        when(helperRepository.findAll()).thenReturn(List.of(helper(uuid(21))));

        var results = housekeepingService.getRegisteredHelpers();

        assertEquals(1, results.size());
        assertEquals("Helper " + uuid(21), results.getFirst().getName());
        assertEquals(Set.of("Central"), results.getFirst().getLocalities());
    }

    @Test
    @DisplayName("Returns registered customers as customer response records")
    void getRegisteredCustomers() {
        when(customerRepository.findAll()).thenReturn(List.of(
                Customer.builder().id(uuid(11)).name("Customer").address("Address").build()));

        var results = housekeepingService.getRegisteredCustomers();

        assertEquals(uuid(11), results.getFirst().getId());
        assertEquals("Customer", results.getFirst().getName());
    }

    @Test
    @DisplayName("Returns available slots with helper and rate details")
    void getAvailableSlots() {
        when(availabilityRepository.findAllSlotsByStatusWithHelper(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(slot(uuid(31), helper(uuid(21)))));

        var results = housekeepingService.getAvailableSlots();

        assertEquals(1, results.size());
        assertEquals(uuid(31), results.getFirst().getId());
        assertEquals(uuid(21), results.getFirst().getHelperId());
        assertEquals("Helper " + uuid(21), results.getFirst().getHelperName());
        assertEquals(350.0, results.getFirst().getHourlyRate());
    }

    @Test
    @DisplayName("Returns bookings with recurring series ID and initial payment ID")
    void getAllBookings() {
        Customer customer = Customer.builder().id(uuid(11)).name("Customer").address("Address").build();
        Booking booking = Booking.builder()
                .id(uuid(41))
                .customer(customer)
                .bookingSeries(BookingSeries.builder().id(uuid(51)).build())
                .assignedHelperId(uuid(21))
                .locality("Central")
                .skill(SkillType.CLEANING)
                .bookingDate(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .totalAmount(350.0)
                .status(BookingStatus.CONFIRMED)
                .build();
        Payment initial = payment(uuid(61), uuid(41), PaymentType.BOOKING_PAYMENT);
        Payment adjustment = payment(uuid(62), uuid(41), PaymentType.RESCHEDULE_PAYMENT);
        when(bookingRepository.findAll()).thenReturn(List.of(booking));
        when(paymentRepository.findByBookingIdIn(List.of(uuid(41)))).thenReturn(List.of(initial, adjustment));

        var results = housekeepingService.getAllBookings();

        assertEquals(1, results.size());
        assertEquals(uuid(51), results.getFirst().getSeriesId());
        assertEquals(uuid(61), results.getFirst().getPaymentId());
        assertEquals(uuid(11), results.getFirst().getCustomerId());
    }

    @Test
    @DisplayName("Returns all payment records with provider reference and typed payment method")
    void getAllPayments() {
        Payment payment = payment(uuid(61), uuid(41), PaymentType.BOOKING_PAYMENT);
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
        when(systemEventRepository.findAllFiltered(uuid(1), uuid(2), uuid(3), uuid(4), uuid(5))).thenReturn(events);

        assertEquals(events, housekeepingService.getAllEvents(uuid(1), uuid(2), uuid(3), uuid(4), uuid(5)));

        verify(systemEventRepository).findAllFiltered(uuid(1), uuid(2), uuid(3), uuid(4), uuid(5));
    }

    private Helper helper(UUID id) {
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

    private HelperAvailability slot(UUID id, Helper helper) {
        return HelperAvailability.builder()
                .id(id)
                .helper(helper)
                .slotDate(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status(AvailabilityStatus.AVAILABLE)
                .build();
    }

    private Payment payment(UUID id, UUID bookingId, PaymentType type) {
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
