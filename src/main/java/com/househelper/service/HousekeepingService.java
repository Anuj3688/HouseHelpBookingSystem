package com.househelper.service;

import com.househelper.dto.AvailableSlotResponse;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.CustomerResponse;
import com.househelper.dto.HelperSearchResponse;
import com.househelper.dto.PaymentResponse;
import com.househelper.model.SystemEvent;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Booking;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.Payment;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import com.househelper.repository.HelperRepository;
import com.househelper.repository.PaymentRepository;
import com.househelper.repository.SystemEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HousekeepingService {

    private final HelperAvailabilityRepository availabilityRepository;
    private final CustomerRepository customerRepository;
    private final HelperRepository helperRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SystemEventRepository systemEventRepository;

    @Transactional(readOnly = true)
    public List<HelperSearchResponse> getAvailableHelpers() {
        Map<Long, HelperSearchResponse> helpersById = new LinkedHashMap<>();
        availabilityRepository.findAllSlotsByStatusWithHelper(AvailabilityStatus.AVAILABLE).forEach(slot -> {
            Helper helper = slot.getHelper();
            helpersById.putIfAbsent(helper.getId(), toHelperResponse(helper));
        });
        return List.copyOf(helpersById.values());
    }

    @Transactional(readOnly = true)
    public List<HelperSearchResponse> getRegisteredHelpers() {
        return helperRepository.findAll().stream().map(this::toHelperResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getRegisteredCustomers() {
        return customerRepository.findAll().stream()
                .map(customer -> CustomerResponse.builder()
                        .id(customer.getId())
                        .name(customer.getName())
                        .address(customer.getAddress())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> getAvailableSlots() {
        return availabilityRepository.findAllSlotsByStatusWithHelper(AvailabilityStatus.AVAILABLE).stream()
                .map(this::toAvailableSlotResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(this::toBookingResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SystemEvent> getAllEvents() {
        return systemEventRepository.findAllByOrderByCreatedAtDesc();
    }

    private HelperSearchResponse toHelperResponse(Helper helper) {
        return HelperSearchResponse.builder()
                .id(helper.getId())
                .name(helper.getName())
                .gender(helper.getGender())
                .localities(new HashSet<>(helper.getLocalities()))
                .skills(new HashSet<>(helper.getSkills()))
                .hourlyRate(helper.getHourlyRate())
                .rating(helper.getRating())
                .ratingCount(helper.getRatingCount())
                .build();
    }

    private AvailableSlotResponse toAvailableSlotResponse(HelperAvailability slot) {
        Helper helper = slot.getHelper();
        return AvailableSlotResponse.builder()
                .id(slot.getId())
                .helperId(helper.getId())
                .helperName(helper.getName())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus())
                .hourlyRate(helper.getHourlyRate())
                .rating(helper.getRating())
                .ratingCount(helper.getRatingCount())
                .build();
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
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

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .bookingId(payment.getBookingId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .build();
    }
}
