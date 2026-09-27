package com.househelper.service;

import com.househelper.dto.BookingResponse;
import com.househelper.dto.CustomerRequest;
import com.househelper.dto.CustomerResponse;
import com.househelper.exception.ConflictException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.model.Booking;
import com.househelper.model.BookingSeries;
import com.househelper.model.BookingStatus;
import com.househelper.model.Customer;
import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentType;
import com.househelper.model.SkillType;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    @DisplayName("Creates a customer after trimming the supplied name and address")
    void createCustomer() {
        CustomerRequest request = new CustomerRequest("  Asha  ", "  Example Street  ");
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(CUSTOMER_ID);
            return saved;
        });

        CustomerResponse response = customerService.createCustomer(request);

        assertEquals(CUSTOMER_ID, response.getId());
        assertEquals("Asha", response.getName());
        assertEquals("Example Street", response.getAddress());
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

    private Customer customer(UUID id, String name, String address) {
        return Customer.builder().id(id).name(name).address(address).build();
    }

    private Booking booking(UUID id, Customer customer, BookingSeries series) {
        return Booking.builder()
                .id(id)
                .customer(customer)
                .bookingSeries(series)
                .assignedHelperId(uuid(5))
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
