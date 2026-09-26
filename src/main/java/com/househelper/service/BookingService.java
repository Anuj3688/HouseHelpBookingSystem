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
import com.househelper.model.BookingStatus;
import com.househelper.model.Customer;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.Payment;
import com.househelper.model.PaymentStatus;
import com.househelper.model.SkillType;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import com.househelper.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@Slf4j
public class BookingService {

    private static final int MAX_ALLOCATION_ATTEMPTS = 3;

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final HelperAvailabilityRepository availabilityRepository;
    private final PaymentRepository paymentRepository;
    private final EventPublisherService eventPublisherService;
    private final TransactionTemplate transactionTemplate;

    public BookingService(BookingRepository bookingRepository,
                          CustomerRepository customerRepository,
                          HelperAvailabilityRepository availabilityRepository,
                          PaymentRepository paymentRepository,
                          EventPublisherService eventPublisherService,
                          PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.availabilityRepository = availabilityRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisherService = eventPublisherService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public BookingResponse createBooking(BookingRequest request) {
        validatePeriod(request.getStartTime(), request.getEndTime());
        return withOptimisticRetries(() -> createBookingAttempt(request));
    }

    public BookingResponse rescheduleBooking(Long bookingId, RescheduleRequest request) {
        validatePeriod(request.getNewStartTime(), request.getNewEndTime());
        return withOptimisticRetries(() -> rescheduleBookingAttempt(bookingId, request));
    }

    public BookingResponse cancelBooking(Long bookingId) {
        return transactionTemplate.execute(status -> cancelBookingInTransaction(bookingId));
    }

    @Transactional
    public void cancelBookingAfterPaymentFailure(Long bookingId, Long failedPaymentId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        releaseCurrentSlot(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        payments.stream()
                .filter(payment -> !payment.getId().equals(failedPaymentId))
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.PENDING
                        || payment.getPaymentStatus() == PaymentStatus.SUCCESS)
                .forEach(payment -> payment.setPaymentStatus(PaymentStatus.REFUNDED));
        paymentRepository.saveAll(payments);

        BookingResponse response = toResponse(booking);
        eventPublisherService.publishEvent("BOOKING_CANCELLED_AFTER_PAYMENT_FAILURE", "Booking",
                booking.getId().toString(), booking.getAssignedHelperId(), booking.getCustomer().getId(),
                failedPaymentId, booking.getId(),
                Map.of("booking", response, "failedPaymentId", failedPaymentId));
    }

    private BookingResponse createBookingAttempt(BookingRequest request) {
        return transactionTemplate.execute(status -> createBookingInTransaction(request));
    }

    private BookingResponse createBookingInTransaction(BookingRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer " + request.getCustomerId() + " was not found."));

        HelperAvailability slot = findAvailableSlot(
                request.getLocality().trim(), request.getSkill(), request.getBookingDate(),
                request.getStartTime(), request.getEndTime(), "No helper is available for the requested slot.");

        slot.setStatus(AvailabilityStatus.BOOKED);

        Helper helper = slot.getHelper();
        BigDecimal amount = calculateAmount(helper.getHourlyRate(), request.getStartTime(), request.getEndTime());
        Booking booking = Booking.builder()
                .customer(customer)
                .assignedHelperId(helper.getId())
                .locality(request.getLocality().trim())
                .skill(request.getSkill())
                .bookingDate(request.getBookingDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .totalAmount(amount.doubleValue())
                .status(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.save(booking);
        Payment payment = savePayment(booking.getId(), amount, request.getPaymentMethod(), PaymentStatus.PENDING);

        return saveAndPublish(booking, "BOOKING_CREATED", payment.getId(), response -> response);
    }

    private BookingResponse rescheduleBookingAttempt(Long bookingId, RescheduleRequest request) {
        return transactionTemplate.execute(status -> rescheduleInTransaction(bookingId, request));
    }

    private BookingResponse rescheduleInTransaction(Long bookingId, RescheduleRequest request) {
        Booking booking = requireBooking(bookingId);
        validateCanReschedule(booking, request);

        HelperAvailability newSlot = findAvailableSlot(
                booking.getLocality(), booking.getSkill(), request.getNewBookingDate(),
                request.getNewStartTime(), request.getNewEndTime(),
                "No helper is available for the requested reschedule slot.");
        newSlot.setStatus(AvailabilityStatus.BOOKED);
        releaseCurrentSlot(booking);

        BigDecimal newAmount = calculateAmount(newSlot.getHelper().getHourlyRate(),
                request.getNewStartTime(), request.getNewEndTime());
        BigDecimal delta = newAmount.subtract(BigDecimal.valueOf(booking.getTotalAmount()));
        updateBookingForReschedule(booking, newSlot, request, newAmount);
        Payment payment = saveReschedulePayment(bookingId, booking.getId(), delta);

        return saveAndPublish(booking, "BOOKING_RESCHEDULED", payment == null ? null : payment.getId(),
                updatedResponse -> Map.of("booking", updatedResponse, "priceDelta", delta));
    }

    private Booking requireBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " was not found."));
    }

    private void validateCanReschedule(Booking booking, RescheduleRequest request) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("A cancelled booking cannot be rescheduled.");
        }
        if (booking.getBookingDate().equals(request.getNewBookingDate())
                && booking.getStartTime().equals(request.getNewStartTime())
                && booking.getEndTime().equals(request.getNewEndTime())) {
            throw new InvalidRequestException("The requested time is the same as the current booking.");
        }
    }

    private HelperAvailability findAvailableSlot(String locality,
                                                  SkillType skill,
                                                  LocalDate bookingDate,
                                                  LocalTime startTime,
                                                  LocalTime endTime,
                                                  String unavailableMessage) {
        List<HelperAvailability> candidates = availabilityRepository.findAvailableHelpersForSlot(
                locality, skill, bookingDate, startTime, endTime, AvailabilityStatus.AVAILABLE);
        for (HelperAvailability candidate : candidates) {
            HelperAvailability slot = availabilityRepository.findById(candidate.getId())
                    .orElseThrow(() -> new SlotUnavailableException(
                            "The selected availability slot no longer exists."));
            if (slot.getStatus() == AvailabilityStatus.AVAILABLE) {
                return slot;
            }
        }
        throw new SlotUnavailableException(unavailableMessage);
    }

    private void releaseCurrentSlot(Booking booking) {
        availabilityRepository.findByHelperIdAndSlotDateAndStartTime(
                        booking.getAssignedHelperId(), booking.getBookingDate(), booking.getStartTime())
                .ifPresent(slot -> slot.setStatus(AvailabilityStatus.AVAILABLE));
    }

    private void updateBookingForReschedule(Booking booking,
                                            HelperAvailability newSlot,
                                            RescheduleRequest request,
                                            BigDecimal newAmount) {
        booking.setAssignedHelperId(newSlot.getHelper().getId());
        booking.setBookingDate(request.getNewBookingDate());
        booking.setStartTime(request.getNewStartTime());
        booking.setEndTime(request.getNewEndTime());
        booking.setTotalAmount(newAmount.doubleValue());
        booking.setStatus(BookingStatus.RESCHEDULED);
    }

    private Payment saveReschedulePayment(Long bookingId, Long paymentBookingId, BigDecimal delta) {
        if (delta.signum() == 0) {
            return null;
        }

        PaymentStatus status = delta.signum() > 0 ? PaymentStatus.PENDING : PaymentStatus.REFUNDED;
        return savePayment(paymentBookingId, delta.abs(), paymentMethodFor(bookingId), status);
    }

    private Payment savePayment(Long bookingId, BigDecimal amount, String paymentMethod, PaymentStatus status) {
        return paymentRepository.save(Payment.builder()
                .bookingId(bookingId)
                .amount(amount.doubleValue())
                .paymentMethod(paymentMethod.trim())
                .paymentStatus(status)
                .build());
    }

    private BookingResponse saveAndPublish(Booking booking,
                                           String eventType,
                                           Long paymentId,
                                           Function<BookingResponse, Object> eventPayloadFactory) {
        bookingRepository.save(booking);
        BookingResponse response = toResponse(booking);
        eventPublisherService.publishEvent(eventType, "Booking", booking.getId().toString(),
                booking.getAssignedHelperId(), booking.getCustomer().getId(), paymentId, booking.getId(),
                eventPayloadFactory.apply(response));
        return response;
    }

    private BookingResponse cancelBookingInTransaction(Long bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking " + bookingId + " is already cancelled.");
        }

        releaseCurrentSlot(booking);
        booking.setStatus(BookingStatus.CANCELLED);

        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        payments.forEach(payment -> payment.setPaymentStatus(PaymentStatus.REFUNDED));
        paymentRepository.saveAll(payments);

        List<Long> paymentIds = payments.stream().map(Payment::getId).toList();
        return saveAndPublish(booking, "BOOKING_CANCELLED", null,
                response -> Map.of("booking", response, "paymentIds", paymentIds));
    }

    private <T> T withOptimisticRetries(Supplier<T> operation) {
        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ALLOCATION_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException exception) {
                lastFailure = exception;
            }
        }
        log.error("Optimistic locking retries exhausted after {} attempts", MAX_ALLOCATION_ATTEMPTS, lastFailure);
        throw new SlotUnavailableException(
                "The slot changed while booking was in progress; retry the request.", lastFailure);
    }

    private void validatePeriod(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new InvalidRequestException("End time must be later than start time.");
        }
    }

    private BigDecimal calculateAmount(Double hourlyRate, LocalTime startTime, LocalTime endTime) {
        long seconds = Duration.between(startTime, endTime).getSeconds();
        return BigDecimal.valueOf(hourlyRate)
                .multiply(BigDecimal.valueOf(seconds))
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private String paymentMethodFor(Long bookingId) {
        return paymentRepository.findByBookingId(bookingId).stream()
                .findFirst()
                .map(Payment::getPaymentMethod)
                .orElse("UNSPECIFIED");
    }

    private BookingResponse toResponse(Booking booking) {
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
}
