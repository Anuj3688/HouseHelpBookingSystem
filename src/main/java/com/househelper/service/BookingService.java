package com.househelper.service;

import com.househelper.dto.BookingRequest;
import com.househelper.dto.BookingResponse;
import com.househelper.dto.InstantBookingRequest;
import com.househelper.dto.RescheduleRequest;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.exception.SlotUnavailableException;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Booking;
import com.househelper.model.BookingSeries;
import com.househelper.model.BookingStatus;
import com.househelper.model.BookingType;
import com.househelper.model.Customer;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.Payment;
import com.househelper.model.PaymentType;
import com.househelper.model.SkillType;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.CustomerRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.UUID;

@Service
@Slf4j
public class BookingService {

    private static final int MAX_ALLOCATION_ATTEMPTS = 3;

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final HelperAvailabilityRepository availabilityRepository;
    private final PaymentRecordService paymentRecordService;
    private final EventPublisherService eventPublisherService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository,
                          CustomerRepository customerRepository,
                          HelperAvailabilityRepository availabilityRepository,
                          PaymentRecordService paymentRecordService,
                          EventPublisherService eventPublisherService,
                          PlatformTransactionManager transactionManager,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.availabilityRepository = availabilityRepository;
        this.paymentRecordService = paymentRecordService;
        this.eventPublisherService = eventPublisherService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    private void validateFutureSlot(LocalDate date, LocalTime startTime) {
        if (date == null || startTime == null
                || !LocalDateTime.of(date, startTime).isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("Scheduled bookings must start in the future.");
        }
    }

    public BookingResponse createBooking(BookingRequest request) {
        return createBooking(request, BookingType.SCHEDULED);
    }

    public BookingResponse createInstantBooking(InstantBookingRequest request) {
        customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer " + request.getCustomerId() + " was not found."));
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        LocalTime earliestStart = now.truncatedTo(ChronoUnit.HOURS);
        if (!now.equals(earliestStart)) {
            earliestStart = earliestStart.plusHours(1);
        }

        List<HelperAvailability> candidates = availabilityRepository.findAvailableSlotsFrom(
                request.getLocality().trim(), request.getSkill(), today, earliestStart, AvailabilityStatus.AVAILABLE);
        LinkedHashSet<LocalTime> candidateStartTimes = new LinkedHashSet<>();
        candidates.forEach(candidate -> candidateStartTimes.add(candidate.getStartTime()));
        for (LocalTime startTime : candidateStartTimes) {
            BookingRequest bookingRequest = BookingRequest.builder()
                    .customerId(request.getCustomerId())
                    .locality(request.getLocality())
                    .skill(request.getSkill())
                    .bookingDate(today)
                    .startTime(startTime)
                    .endTime(startTime.plusHours(1))
                    .paymentMethod(request.getPaymentMethod())
                    .build();
            try {
                return createBooking(bookingRequest, BookingType.INSTANT);
            } catch (SlotUnavailableException exception) {
                log.info("Instant booking slot became unavailable; trying the next slot at {}", startTime);
            }
        }
        throw new SlotUnavailableException("No matching helper is available for instant booking today.");
    }

    private BookingResponse createBooking(BookingRequest request, BookingType bookingType) {
        if (bookingType == BookingType.SCHEDULED) {
            validateFutureSlot(request.getBookingDate(), request.getStartTime());
        }
        validatePeriod(request.getStartTime(), request.getEndTime());
        return withOptimisticRetries(() -> createBookingAttempt(request, bookingType));
    }

    public BookingResponse createRecurringBookingOccurrence(BookingSeries series, BookingRequest request) {
        validateFutureSlot(request.getBookingDate(), request.getStartTime());
        validatePeriod(request.getStartTime(), request.getEndTime());
        return withOptimisticRetries(() -> transactionTemplate.execute(status ->
                createBookingInTransaction(request, series, BookingType.RECURRING)));
    }

    public BookingResponse rescheduleBooking(UUID bookingId, RescheduleRequest request) {
        validatePeriod(request.getNewStartTime(), request.getNewEndTime());
        return withOptimisticRetries(() -> rescheduleBookingAttempt(bookingId, request));
    }

    public BookingResponse cancelBooking(UUID bookingId) {
        return transactionTemplate.execute(status -> cancelBookingInTransaction(bookingId));
    }

    @Transactional
    public void cancelBookingAfterPaymentFailure(UUID bookingId, UUID failedPaymentId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        releaseCurrentSlot(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Optional<Payment> refund = paymentRecordService.createCancellationRefund(bookingId,
                booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId(), failedPaymentId);

        BookingResponse response = toResponse(booking);
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("booking", response);
        eventPayload.put("failedPaymentId", failedPaymentId);
        eventPayload.put("refundPaymentId", refund.map(Payment::getId).orElse(null));
        eventPublisherService.publishEvent("BOOKING_CANCELLED_AFTER_PAYMENT_FAILURE", "Booking",
                booking.getId().toString(), booking.getAssignedHelperId(), booking.getCustomer().getId(),
                failedPaymentId, booking.getId(), booking.getBookingSeries() == null
                        ? null : booking.getBookingSeries().getId(), eventPayload);
    }

    @Transactional
    public void confirmBookingAfterPaymentSuccess(UUID bookingId, UUID paymentId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            Optional<Payment> refund;
            UUID seriesId = booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId();
            if (seriesId == null) {
                refund = paymentRecordService.createCancellationRefund(bookingId, null, null);
            } else {
                refund = paymentRecordService.createSeriesCancellationRefund(
                        seriesId, List.of());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("capturedPaymentId", paymentId);
            payload.put("refundPaymentId", refund.map(Payment::getId).orElse(null));
            eventPublisherService.publishEvent("PAYMENT_SUCCEEDED_AFTER_BOOKING_CANCELLATION", "Booking",
                    booking.getId().toString(), booking.getAssignedHelperId(), booking.getCustomer().getId(),
                    paymentId, booking.getId(), seriesId, payload);
            return;
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        eventPublisherService.publishEvent("BOOKING_CONFIRMED_AFTER_PAYMENT", "Booking",
                booking.getId().toString(), booking.getAssignedHelperId(), booking.getCustomer().getId(),
                paymentId, booking.getId(), booking.getBookingSeries() == null
                        ? null : booking.getBookingSeries().getId(), toResponse(booking));
    }

    private BookingResponse createBookingAttempt(BookingRequest request, BookingType bookingType) {
        return transactionTemplate.execute(status -> createBookingInTransaction(request, null, bookingType));
    }

    private BookingResponse createBookingInTransaction(BookingRequest request, BookingSeries series,
                                                       BookingType bookingType) {
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
                .bookingSeries(series)
                .assignedHelperId(helper.getId())
                .locality(request.getLocality().trim())
                .skill(request.getSkill())
                .bookingDate(request.getBookingDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .totalAmount(amount.doubleValue())
                .status(BookingStatus.PENDING_PAYMENT)
                .bookingType(bookingType)
                .build();
        bookingRepository.save(booking);
        Payment payment = paymentRecordService.createBookingPayment(
                booking.getId(), series == null ? null : series.getId(), amount, request.getPaymentMethod());

        return saveAndPublish(booking, "BOOKING_CREATED", payment.getId(), response -> response);
    }

    private BookingResponse rescheduleBookingAttempt(UUID bookingId, RescheduleRequest request) {
        return transactionTemplate.execute(status -> rescheduleInTransaction(bookingId, request));
    }

    private BookingResponse rescheduleInTransaction(UUID bookingId, RescheduleRequest request) {
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
        Payment payment = paymentRecordService.createRescheduleAdjustment(bookingId,
                booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId(), delta);

        return saveAndPublish(booking, "BOOKING_RESCHEDULED", payment == null ? null : payment.getId(),
                updatedResponse -> Map.of("booking", updatedResponse, "priceDelta", delta));
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " was not found."));
    }

    private void validateCanReschedule(Booking booking, RescheduleRequest request) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("A cancelled booking cannot be rescheduled.");
        }
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("A booking cannot be rescheduled until its payment is successful.");
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
        HelperAvailability slot = availabilityRepository.findByHelperIdAndSlotDateAndStartTime(
                        booking.getAssignedHelperId(), booking.getBookingDate(), booking.getStartTime())
                .orElseThrow(() -> new ConflictException(
                        "The availability slot for booking " + booking.getId() + " was not found."));
        slot.setStatus(AvailabilityStatus.AVAILABLE);
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

    private BookingResponse saveAndPublish(Booking booking,
                                           String eventType,
                                           UUID paymentId,
                                           Function<BookingResponse, Object> eventPayloadFactory) {
        bookingRepository.save(booking);
        BookingResponse response = toResponse(booking);
        response.setPaymentId(paymentId);
        eventPublisherService.publishEvent(eventType, "Booking", booking.getId().toString(),
                booking.getAssignedHelperId(), booking.getCustomer().getId(), paymentId, booking.getId(),
                booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId(),
                eventPayloadFactory.apply(response));
        return response;
    }

    private BookingResponse cancelBookingInTransaction(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking " + bookingId + " is already cancelled.");
        }

        releaseCurrentSlot(booking);
        booking.setStatus(BookingStatus.CANCELLED);

        List<Payment> payments = paymentRecordService.findPaymentsForBooking(bookingId);
        List<UUID> paymentIds = payments.stream().map(Payment::getId).toList();
        Optional<Payment> refund = paymentRecordService.createCancellationRefund(bookingId,
                booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId(), null);
        return saveAndPublish(booking, "BOOKING_CANCELLED", null,
                response -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("booking", response);
                    payload.put("paymentIds", paymentIds);
                    payload.put("refundPaymentId", refund.map(Payment::getId).orElse(null));
                    return payload;
                });
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

    private BookingResponse toResponse(Booking booking) {
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
                .bookingType(booking.getBookingType())
                .build();
    }
}
