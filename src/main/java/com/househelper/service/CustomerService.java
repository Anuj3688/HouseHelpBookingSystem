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
import com.househelper.model.BookingStatus;
import com.househelper.model.Customer;
import com.househelper.model.CustomerReview;
import com.househelper.model.Payment;
import com.househelper.model.PaymentType;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.CustomerReviewRepository;
import com.househelper.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerReviewRepository customerReviewRepository;
    private final EventPublisherService eventPublisherService;
    private final Clock clock;

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        Customer customer = Customer.builder()
                .name(request.getName().trim())
                .address(request.getAddress().trim())
                .phone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim())
                .email(request.getEmail() == null || request.getEmail().isBlank() ? null : request.getEmail().trim())
                .totalRating(BigDecimal.ZERO)
                .ratingCount(0L)
                .build();
        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID customerId) {
        return toResponse(requireCustomer(customerId));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getCustomerBookings(UUID customerId) {
        requireCustomer(customerId);
        List<Booking> bookings = bookingRepository.findByCustomer_IdOrderByBookingDateAscStartTimeAsc(customerId);
        Map<UUID, UUID> initialPaymentIds = bookings.isEmpty()
                ? Map.of()
                : paymentRepository.findByBookingIdIn(bookings.stream().map(Booking::getId).toList())
                        .stream()
                        .filter(payment -> payment.getPaymentType() == PaymentType.BOOKING_PAYMENT)
                        .collect(Collectors.toMap(Payment::getBookingId, Payment::getId, (left, right) ->
                                left.compareTo(right) <= 0 ? left : right));
        return bookings.stream()
                .map(booking -> toBookingResponse(booking, initialPaymentIds.get(booking.getId())))
                .toList();
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID customerId, CustomerRequest request) {
        Customer customer = requireCustomer(customerId);
        customer.setName(request.getName().trim());
        customer.setAddress(request.getAddress().trim());
        if (request.getPhone() != null) {
            customer.setPhone(request.getPhone().isBlank() ? null : request.getPhone().trim());
        }
        if (request.getEmail() != null) {
            customer.setEmail(request.getEmail().isBlank() ? null : request.getEmail().trim());
        }
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void deleteCustomer(UUID customerId) {
        Customer customer = requireCustomer(customerId);
        if (bookingRepository.existsByCustomer_Id(customerId)) {
            throw new ConflictException("A customer with booking history cannot be deleted.");
        }
        customerRepository.delete(customer);
    }

    @Transactional
    public CustomerReviewResponse addCustomerReview(UUID customerId, CustomerReviewRequest request) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + customerId + " was not found."));

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + request.getBookingId() + " was not found."));

        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new InvalidRequestException("Booking " + request.getBookingId() + " does not belong to customer " + customerId + ".");
        }

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new InvalidRequestException("A customer review can only be submitted after service completion (booking status must be COMPLETED).");
        }

        UUID reviewingHelperId = request.getHelperId() != null ? request.getHelperId() : booking.getAssignedHelperId();
        if (!booking.getAssignedHelperId().equals(reviewingHelperId)) {
            throw new InvalidRequestException("Only the assigned helper for booking " + booking.getId() + " can submit a customer review.");
        }

        if (customerReviewRepository.existsByBookingId(booking.getId())) {
            throw new ConflictException("A review has already been submitted for booking " + booking.getId() + ".");
        }

        CustomerReview review = CustomerReview.builder()
                .customer(customer)
                .helperId(reviewingHelperId)
                .bookingId(booking.getId())
                .rating(request.getRating())
                .review(request.getReview() == null || request.getReview().isBlank() ? null : request.getReview().trim())
                .createdAt(Instant.now(clock))
                .build();
        CustomerReview savedReview = customerReviewRepository.save(review);

        customer.setTotalRating(customer.getTotalRating().add(BigDecimal.valueOf(request.getRating())));
        customer.setRatingCount(customer.getRatingCount() + 1);
        customerRepository.save(customer);

        eventPublisherService.publishEvent("CUSTOMER_REVIEWED", "Customer",
                customer.getId().toString(), reviewingHelperId, customer.getId(), null, booking.getId(),
                booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId(),
                Map.of(
                        "customerId", customer.getId(),
                        "bookingId", booking.getId(),
                        "helperId", reviewingHelperId,
                        "rating", request.getRating(),
                        "averageRating", customer.getRating()));

        return toReviewResponse(savedReview, customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerReviewResponse> getCustomerReviews(UUID customerId) {
        Customer customer = requireCustomer(customerId);
        return customerReviewRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(review -> toReviewResponse(review, customer))
                .toList();
    }

    private Customer requireCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + customerId + " was not found."));
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .address(customer.getAddress())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .rating(customer.getRating())
                .ratingCount(customer.getRatingCount())
                .build();
    }

    private CustomerReviewResponse toReviewResponse(CustomerReview review, Customer customer) {
        return CustomerReviewResponse.builder()
                .id(review.getId())
                .customerId(customer.getId())
                .helperId(review.getHelperId())
                .bookingId(review.getBookingId())
                .rating(review.getRating())
                .review(review.getReview())
                .createdAt(review.getCreatedAt())
                .customerAverageRating(customer.getRating())
                .customerRatingCount(customer.getRatingCount())
                .build();
    }

    private BookingResponse toBookingResponse(Booking booking, UUID paymentId) {
        return BookingResponse.builder()
                .id(booking.getId())
                .seriesId(booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId())
                .paymentId(paymentId)
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
