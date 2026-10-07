package com.househelper.service;

import com.househelper.dto.ReassignmentTaskResponse;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Booking;
import com.househelper.model.BookingStatus;
import com.househelper.model.Customer;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentType;
import com.househelper.model.ReassignmentTask;
import com.househelper.model.ReassignmentTaskStatus;
import com.househelper.model.SkillType;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import com.househelper.repository.ReassignmentTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReassignmentQueueServiceTest {

    private static UUID uuid(long value) {
        return UUID.nameUUIDFromBytes(("test-" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final UUID TASK_ID = uuid(1);
    private static final UUID BOOKING_ID = uuid(10);
    private static final UUID ORIGINAL_HELPER_ID = uuid(100);
    private static final UUID SUBSTITUTE_HELPER_ID = uuid(200);

    @Mock
    private ReassignmentTaskRepository taskRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private HelperAvailabilityRepository availabilityRepository;

    @Mock
    private PaymentRecordService paymentRecordService;

    @Mock
    private EventPublisherService eventPublisherService;

    private Clock clock;
    private ReassignmentQueueService queueService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);
        queueService = new ReassignmentQueueService(
                taskRepository, bookingRepository, availabilityRepository,
                paymentRecordService, eventPublisherService, clock);
    }

    @Test
    @DisplayName("enqueueTask creates and persists a pending reassignment task")
    void enqueueTask() {
        when(taskRepository.save(any(ReassignmentTask.class))).thenAnswer(invocation -> {
            ReassignmentTask task = invocation.getArgument(0);
            task.setId(TASK_ID);
            return task;
        });

        ReassignmentTask created = queueService.enqueueTask(BOOKING_ID, ORIGINAL_HELPER_ID, "Medical Emergency");

        assertNotNull(created);
        assertEquals(TASK_ID, created.getId());
        assertEquals(BOOKING_ID, created.getBookingId());
        assertEquals(ORIGINAL_HELPER_ID, created.getOriginalHelperId());
        assertEquals("Medical Emergency", created.getReason());
        assertEquals(ReassignmentTaskStatus.PENDING, created.getStatus());
        assertEquals(0, created.getAttempts());
        assertEquals(clock.instant(), created.getCreatedAt());
    }

    @Test
    @DisplayName("processSingleTask reassigns booking when qualified substitute helper is available")
    void processSingleTaskSuccess() {
        Customer customer = Customer.builder().id(uuid(50)).name("Asha").build();
        Booking booking = Booking.builder()
                .id(BOOKING_ID)
                .customer(customer)
                .assignedHelperId(ORIGINAL_HELPER_ID)
                .locality("Central")
                .skill(SkillType.CLEANING)
                .bookingDate(LocalDate.of(2026, 10, 15))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .totalAmount(500.0)
                .status(BookingStatus.PENDING_REASSIGNMENT)
                .build();

        ReassignmentTask task = ReassignmentTask.builder()
                .id(TASK_ID)
                .bookingId(BOOKING_ID)
                .originalHelperId(ORIGINAL_HELPER_ID)
                .status(ReassignmentTaskStatus.PENDING)
                .attempts(0)
                .createdAt(clock.instant())
                .build();

        Helper substitute = Helper.builder()
                .id(SUBSTITUTE_HELPER_ID)
                .name("Rani")
                .hourlyRate(300.0) // higher rate, but customer price is protected!
                .build();

        HelperAvailability slot1 = HelperAvailability.builder()
                .id(uuid(301))
                .helper(substitute)
                .slotDate(LocalDate.of(2026, 10, 15))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status(AvailabilityStatus.AVAILABLE)
                .build();

        HelperAvailability slot2 = HelperAvailability.builder()
                .id(uuid(302))
                .helper(substitute)
                .slotDate(LocalDate.of(2026, 10, 15))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status(AvailabilityStatus.AVAILABLE)
                .build();

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Candidate search: returns substitute helper for the first hour
        when(availabilityRepository.findAvailableHelpersForSlot(
                "Central", SkillType.CLEANING, LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0), AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(slot1));

        // Window lookup: returns both continuous hours
        when(availabilityRepository.findSlotsInWindow(
                SUBSTITUTE_HELPER_ID, LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(11, 0), AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(slot1, slot2));

        when(taskRepository.save(any(ReassignmentTask.class))).thenAnswer(i -> i.getArgument(0));

        ReassignmentTask result = queueService.processSingleTask(TASK_ID);

        assertEquals(ReassignmentTaskStatus.REASSIGNED, result.getStatus());
        assertEquals(SUBSTITUTE_HELPER_ID, result.getReassignedHelperId());
        assertEquals(1, result.getAttempts());

        // Booking verified updated
        assertEquals(SUBSTITUTE_HELPER_ID, booking.getAssignedHelperId());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(500.0, booking.getTotalAmount()); // Price protected!

        // Substitute slots reserved
        assertEquals(AvailabilityStatus.BOOKED, slot1.getStatus());
        assertEquals(AvailabilityStatus.BOOKED, slot2.getStatus());
        verify(availabilityRepository).saveAll(List.of(slot1, slot2));
        verify(bookingRepository).save(booking);
        verify(paymentRecordService, never()).createCancellationRefund(any(), any(), any());
    }

    @Test
    @DisplayName("processSingleTask falls back to cancellation and refund when no substitute is available")
    void processSingleTaskNoSubstituteFallback() {
        Customer customer = Customer.builder().id(uuid(50)).name("Asha").build();
        Booking booking = Booking.builder()
                .id(BOOKING_ID)
                .customer(customer)
                .assignedHelperId(ORIGINAL_HELPER_ID)
                .locality("Central")
                .skill(SkillType.CLEANING)
                .bookingDate(LocalDate.of(2026, 10, 15))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .totalAmount(250.0)
                .status(BookingStatus.PENDING_REASSIGNMENT)
                .build();

        ReassignmentTask task = ReassignmentTask.builder()
                .id(TASK_ID)
                .bookingId(BOOKING_ID)
                .originalHelperId(ORIGINAL_HELPER_ID)
                .status(ReassignmentTaskStatus.PENDING)
                .attempts(0)
                .createdAt(clock.instant())
                .build();

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findAvailableHelpersForSlot(
                "Central", SkillType.CLEANING, LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0), AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of()); // No candidate

        Payment refund = Payment.builder().id(uuid(999)).paymentType(PaymentType.CANCEL_REFUND).build();
        when(paymentRecordService.createCancellationRefund(BOOKING_ID, null, null))
                .thenReturn(Optional.of(refund));
        when(taskRepository.save(any(ReassignmentTask.class))).thenAnswer(i -> i.getArgument(0));

        ReassignmentTask result = queueService.processSingleTask(TASK_ID);

        assertEquals(ReassignmentTaskStatus.FAILED_CANCELLED, result.getStatus());
        assertEquals("No available substitute helper found for the requested slot.", result.getFailureReason());
        assertNull(result.getReassignedHelperId());

        // Booking auto-cancelled and 100% refund ledger triggered
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(bookingRepository).save(booking);
        verify(paymentRecordService).createCancellationRefund(BOOKING_ID, null, null);
    }

    @Test
    @DisplayName("processPendingTasks retrieves all pending tasks and returns task responses")
    void processPendingTasks() {
        ReassignmentTask task = ReassignmentTask.builder()
                .id(TASK_ID)
                .bookingId(BOOKING_ID)
                .originalHelperId(ORIGINAL_HELPER_ID)
                .status(ReassignmentTaskStatus.PENDING)
                .createdAt(clock.instant())
                .build();

        when(taskRepository.findByStatusOrderByCreatedAtAsc(ReassignmentTaskStatus.PENDING))
                .thenReturn(List.of(task));
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty()); // will fail gracefully
        when(taskRepository.save(any(ReassignmentTask.class))).thenAnswer(i -> i.getArgument(0));

        List<ReassignmentTaskResponse> results = queueService.processPendingTasks();

        assertEquals(1, results.size());
        assertEquals(ReassignmentTaskStatus.FAILED_CANCELLED, results.get(0).getStatus());
    }
}
