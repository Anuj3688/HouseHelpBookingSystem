package com.househelper.service;

import com.househelper.dto.BookingResponse;
import com.househelper.dto.CustomerRequest;
import com.househelper.dto.CustomerResponse;
import com.househelper.dto.CustomerReviewRequest;
import com.househelper.dto.CustomerReviewResponse;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.model.Booking;
import com.househelper.model.BookingSeries;
import com.househelper.model.BookingStatus;
import com.househelper.model.Customer;
import com.househelper.model.CustomerReview;
import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentType;
import com.househelper.model.SkillType;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.CustomerReviewRepository;
import com.househelper.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static UUID uuid(long value) {
        return UUID.nameUUIDFromBytes(("test-id-" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final UUID CUSTOMER_ID = uuid(12);
    private static final UUID HELPER_ID = uuid(5);
    private static final UUID BOOKING_ID = uuid(31);

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CustomerReviewRepository reviewRepository;

    @Mock
    private EventPublisherService eventPublisherService;

    private Clock clock;
    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
        customerService = new CustomerService(customerRepository, bookingRepository,
                paymentRepository, reviewRepository, eventPublisherService, clock);
    }

    @Test
    @DisplayName("Creates a customer after trimming the supplied name, address, phone and email")
    void createCustomer() {
        CustomerRequest request = new CustomerRequest("  Asha  ", "  Example Street  ", " 9876543210 ", " asha@test.com ");
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(CUSTOMER_ID);
            return saved;
        });

        CustomerResponse response = customerService.createCustomer(request);

        assertEquals(CUSTOMER_ID, response.getId());
        assertEquals("Asha", response.getName());
        assertEquals("Example Street", response.getAddress());
        assertEquals("9876543210", response.getPhone());
        assertEquals("asha@test.com", response.getEmail());
    }

    @Test
    @DisplayName("Returns all registered customers as response DTOs")
    void getAllCustomers() {
        when(customerRepository.findAll()).thenReturn(List.of(
                customer(uuid(12), "Asha", "Address 1"),
                customer(uuid(13), "Mira", "Address 2")));

        List<CustomerResponse> responses = customerService.getAllCustomers();

        assertEquals(2, responses.size());
        assertEquals("Asha", responses.get(0).getName());
        assertEquals(uuid(13), responses.get(1).getId());
    }

    @Test
    @DisplayName("Returns a customer by ID")
    void getCustomer() {
        when(customerRepository.findById(CUSTOMER_ID))
                .thenReturn(Optional.of(customer(CUSTOMER_ID, "Asha", "Address")));

        CustomerResponse response = customerService.getCustomer(CUSTOMER_ID);

        assertEquals(CUSTOMER_ID, response.getId());
        assertEquals("Asha", response.getName());
        assertEquals("Address", response.getAddress());
    }

    @Test
    @DisplayName("Reports a missing customer when retrieving by ID")
    void getCustomerMissing() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.getCustomer(CUSTOMER_ID));

        verifyNoInteractions(bookingRepository, paymentRepository);
    }

    @Test
    @DisplayName("Returns a customer's bookings with series IDs and initial booking payment IDs")
    void getCustomerBookings() {
        Customer customer = customer(CUSTOMER_ID, "Asha", "Address");
        Booking oneOff = booking(uuid(31), customer, null);
        Booking recurring = booking(uuid(32), customer, BookingSeries.builder().id(uuid(77)).build());
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(bookingRepository.findByCustomer_IdOrderByBookingDateAscStartTimeAsc(CUSTOMER_ID))
                .thenReturn(List.of(oneOff, recurring));
        when(paymentRepository.findByBookingIdIn(List.of(uuid(31), uuid(32)))).thenReturn(List.of(
                payment(uuid(101), uuid(31), PaymentType.BOOKING_PAYMENT),
                payment(uuid(102), uuid(31), PaymentType.RESCHEDULE_PAYMENT),
                payment(uuid(103), uuid(32), PaymentType.BOOKING_PAYMENT)));

        List<BookingResponse> responses = customerService.getCustomerBookings(CUSTOMER_ID);

        assertEquals(2, responses.size());
        assertEquals(uuid(31), responses.get(0).getId());
        assertNull(responses.get(0).getSeriesId());
        assertEquals(uuid(101), responses.get(0).getPaymentId());
        assertEquals(uuid(32), responses.get(1).getId());
        assertEquals(uuid(77), responses.get(1).getSeriesId());
        assertEquals(uuid(103), responses.get(1).getPaymentId());
    }

    @Test
    @DisplayName("Returns an empty booking list without querying payments when the customer has no bookings")
    void getCustomerBookingsEmpty() {
        when(customerRepository.findById(CUSTOMER_ID))
                .thenReturn(Optional.of(customer(CUSTOMER_ID, "Asha", "Address")));
        when(bookingRepository.findByCustomer_IdOrderByBookingDateAscStartTimeAsc(CUSTOMER_ID))
                .thenReturn(List.of());

        List<BookingResponse> responses = customerService.getCustomerBookings(CUSTOMER_ID);

        assertEquals(List.of(), responses);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("Reports a missing customer instead of returning bookings for an unknown ID")
    void getCustomerBookingsMissingCustomer() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.getCustomerBookings(CUSTOMER_ID));

        verifyNoInteractions(bookingRepository, paymentRepository);
    }

    @Test
    @DisplayName("Updates a customer's name and address after trimming whitespace")
    void updateCustomer() {
        Customer customer = customer(CUSTOMER_ID, "Old Name", "Old Address");
        CustomerRequest request = new CustomerRequest("  New Name ", " New Address ");
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        CustomerResponse response = customerService.updateCustomer(CUSTOMER_ID, request);

        assertEquals("New Name", response.getName());
        assertEquals("New Address", response.getAddress());
        verify(customerRepository).save(customer);
    }

    @Test
    @DisplayName("Rejects deleting a customer who has booking history")
    void deleteCustomerWithBookings() {
        when(customerRepository.findById(CUSTOMER_ID))
                .thenReturn(Optional.of(customer(CUSTOMER_ID, "Asha", "Address")));
        when(bookingRepository.existsByCustomer_Id(CUSTOMER_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> customerService.deleteCustomer(CUSTOMER_ID));

        verify(customerRepository, never()).delete(any(Customer.class));
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("Deletes a customer who has no booking history")
    void deleteCustomerWithoutBookings() {
        Customer customer = customer(CUSTOMER_ID, "Asha", "Address");
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(bookingRepository.existsByCustomer_Id(CUSTOMER_ID)).thenReturn(false);

        customerService.deleteCustomer(CUSTOMER_ID);

        verify(customerRepository).delete(customer);
    }

    @Test
    @DisplayName("Reports a missing customer when attempting to delete")
    void deleteCustomerMissing() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.deleteCustomer(CUSTOMER_ID));

        verifyNoInteractions(bookingRepository, paymentRepository);
    }

    @Test
    @DisplayName("Adds a review for a completed booking and updates the customer's average rating")
    void addCustomerReviewSuccess() {
        Customer customer = customer(CUSTOMER_ID, "Asha", "Address");
        Booking booking = booking(BOOKING_ID, customer, null);
        booking.setStatus(BookingStatus.COMPLETED);

        CustomerReviewRequest request = CustomerReviewRequest.builder()
                .bookingId(BOOKING_ID)
                .helperId(HELPER_ID)
                .rating(5)
                .review("Great customer, very polite")
                .build();

        when(customerRepository.findByIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(false);
        when(reviewRepository.save(any(CustomerReview.class))).thenAnswer(invocation -> {
            CustomerReview r = invocation.getArgument(0);
            r.setId(uuid(99));
            return r;
        });

        CustomerReviewResponse response = customerService.addCustomerReview(CUSTOMER_ID, request);

        assertNotNull(response);
        assertEquals(5, response.getRating());
        assertEquals("Great customer, very polite", response.getReview());
        assertEquals(CUSTOMER_ID, response.getCustomerId());
        assertEquals(HELPER_ID, response.getHelperId());

        assertEquals(BigDecimal.valueOf(5), customer.getTotalRating());
        assertEquals(1L, customer.getRatingCount());
        verify(customerRepository).save(customer);
    }

    @Test
    @DisplayName("Rejects review submission if the booking is not completed")
    void addCustomerReviewNotCompleted() {
        Customer customer = customer(CUSTOMER_ID, "Asha", "Address");
        Booking booking = booking(BOOKING_ID, customer, null);
        booking.setStatus(BookingStatus.CONFIRMED);

        CustomerReviewRequest request = CustomerReviewRequest.builder()
                .bookingId(BOOKING_ID)
                .helperId(HELPER_ID)
                .rating(5)
                .build();

        when(customerRepository.findByIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThrows(InvalidRequestException.class, () -> customerService.addCustomerReview(CUSTOMER_ID, request));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects review submission if the booking was already reviewed")
    void addCustomerReviewAlreadyReviewed() {
        Customer customer = customer(CUSTOMER_ID, "Asha", "Address");
        Booking booking = booking(BOOKING_ID, customer, null);
        booking.setStatus(BookingStatus.COMPLETED);

        CustomerReviewRequest request = CustomerReviewRequest.builder()
                .bookingId(BOOKING_ID)
                .helperId(HELPER_ID)
                .rating(5)
                .build();

        when(customerRepository.findByIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> customerService.addCustomerReview(CUSTOMER_ID, request));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects review submission if the helper does not match the booking's assigned helper")
    void addCustomerReviewHelperMismatch() {
        Customer customer = customer(CUSTOMER_ID, "Asha", "Address");
        Booking booking = booking(BOOKING_ID, customer, null);
        booking.setStatus(BookingStatus.COMPLETED);

        CustomerReviewRequest request = CustomerReviewRequest.builder()
                .bookingId(BOOKING_ID)
                .helperId(uuid(999)) // Mismatch
                .rating(5)
                .build();

        when(customerRepository.findByIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThrows(InvalidRequestException.class, () -> customerService.addCustomerReview(CUSTOMER_ID, request));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Returns reviews submitted for a customer ordered by creation date descending")
    void getCustomerReviews() {
        Customer customer = customer(CUSTOMER_ID, "Asha", "Address");
        CustomerReview review = CustomerReview.builder()
                .id(uuid(88))
                .customer(customer)
                .helperId(HELPER_ID)
                .bookingId(BOOKING_ID)
                .rating(4)
                .review("Pleasant experience")
                .createdAt(Instant.now())
                .build();

        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(reviewRepository.findByCustomer_IdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of(review));

        List<CustomerReviewResponse> reviews = customerService.getCustomerReviews(CUSTOMER_ID);

        assertEquals(1, reviews.size());
        assertEquals(4, reviews.get(0).getRating());
        assertEquals("Pleasant experience", reviews.get(0).getReview());
    }

    private Customer customer(UUID id, String name, String address) {
        return Customer.builder().id(id).name(name).address(address).build();
    }

    private Booking booking(UUID id, Customer customer, BookingSeries series) {
        return Booking.builder()
                .id(id)
                .customer(customer)
                .bookingSeries(series)
                .assignedHelperId(HELPER_ID)
                .locality("Central")
                .skill(SkillType.CLEANING)
                .bookingDate(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .totalAmount(350.0)
                .status(BookingStatus.CONFIRMED)
                .build();
    }

    private Payment payment(UUID id, UUID bookingId, PaymentType paymentType) {
        return Payment.builder()
                .id(id)
                .bookingId(bookingId)
                .paymentType(paymentType)
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }
}
