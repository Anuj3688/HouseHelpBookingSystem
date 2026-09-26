package com.househelper.service;

import com.househelper.dto.CustomerRequest;
import com.househelper.dto.CustomerResponse;
import com.househelper.dto.BookingResponse;
import com.househelper.exception.ConflictException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.model.Booking;
import com.househelper.model.Customer;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        Customer customer = Customer.builder()
                .name(request.getName().trim())
                .address(request.getAddress().trim())
                .build();
        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long customerId) {
        return toResponse(requireCustomer(customerId));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getCustomerBookings(Long customerId) {
        requireCustomer(customerId);
        return bookingRepository.findByCustomer_IdOrderByBookingDateAscStartTimeAsc(customerId)
                .stream()
                .map(this::toBookingResponse)
                .toList();
    }

    @Transactional
    public CustomerResponse updateCustomer(Long customerId, CustomerRequest request) {
        Customer customer = requireCustomer(customerId);
        customer.setName(request.getName().trim());
        customer.setAddress(request.getAddress().trim());
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void deleteCustomer(Long customerId) {
        Customer customer = requireCustomer(customerId);
        if (bookingRepository.existsByCustomer_Id(customerId)) {
            throw new ConflictException("A customer with booking history cannot be deleted.");
        }
        customerRepository.delete(customer);
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + customerId + " was not found."));
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .address(customer.getAddress())
                .build();
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .seriesId(booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId())
                .customerId(booking.getCustomer().getId())
                .assignedHelperId(booking.getAssignedHelperId())
                .locality(booking.getLocality())
                .skill(booking.getSkill())
                .bookingDate(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .build();
    }
}
