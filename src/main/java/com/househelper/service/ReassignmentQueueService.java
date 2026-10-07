package com.househelper.service;

import com.househelper.dto.ReassignmentTaskResponse;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Booking;
import com.househelper.model.BookingStatus;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.Payment;
import com.househelper.model.ReassignmentTask;
import com.househelper.model.ReassignmentTaskStatus;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import com.househelper.repository.ReassignmentTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReassignmentQueueService {

    private final ReassignmentTaskRepository taskRepository;
    private final BookingRepository bookingRepository;
    private final HelperAvailabilityRepository availabilityRepository;
    private final PaymentRecordService paymentRecordService;
    private final EventPublisherService eventPublisherService;
    private final Clock clock;

    /**
     * Enqueues a new reassignment task transactionally.
     */
    @Transactional
    public ReassignmentTask enqueueTask(UUID bookingId, UUID originalHelperId, String reason) {
        ReassignmentTask task = ReassignmentTask.builder()
                .bookingId(bookingId)
                .originalHelperId(originalHelperId)
                .reason(reason)
                .status(ReassignmentTaskStatus.PENDING)
                .attempts(0)
                .createdAt(Instant.now(clock))
                .build();
        return taskRepository.save(task);
    }

    /**
     * Triggers asynchronous processing of all pending reassignment tasks.
     * Prevents blocking the helper's cancellation request (Option B).
     */
    public void triggerAsyncProcessing() {
        CompletableFuture.runAsync(() -> {
            try {
                processPendingTasks();
            } catch (Exception ex) {
                log.error("Error during asynchronous reassignment task processing", ex);
            }
        });
    }

    /**
     * Processes all pending tasks in FIFO order. Each task is processed in its own isolated transaction.
     */
    public List<ReassignmentTaskResponse> processPendingTasks() {
        List<ReassignmentTask> pendingTasks = taskRepository
                .findByStatusOrderByCreatedAtAsc(ReassignmentTaskStatus.PENDING);
        List<ReassignmentTaskResponse> results = new ArrayList<>();
        for (ReassignmentTask task : pendingTasks) {
            try {
                ReassignmentTask processed = processSingleTask(task.getId());
                results.add(toResponse(processed));
            } catch (Exception ex) {
                log.error("Failed to process reassignment task {}", task.getId(), ex);
            }
        }
        return results;
    }

    /**
     * Processes an individual task in an isolated transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReassignmentTask processSingleTask(UUID taskId) {
        ReassignmentTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != ReassignmentTaskStatus.PENDING) {
            return task;
        }

        task.setAttempts(task.getAttempts() + 1);
        Booking booking = bookingRepository.findById(task.getBookingId()).orElse(null);

        if (booking == null) {
            task.setStatus(ReassignmentTaskStatus.FAILED_CANCELLED);
            task.setFailureReason("Booking not found.");
            task.setUpdatedAt(Instant.now(clock));
            return taskRepository.save(task);
        }

        if (booking.getStatus() != BookingStatus.PENDING_REASSIGNMENT) {
            task.setStatus(ReassignmentTaskStatus.FAILED_CANCELLED);
            task.setFailureReason("Booking is not in PENDING_REASSIGNMENT status (current: " + booking.getStatus() + ").");
            task.setUpdatedAt(Instant.now(clock));
            return taskRepository.save(task);
        }

        List<HelperAvailability> substituteSlots = findSubstituteSlots(booking, task.getOriginalHelperId());

        if (substituteSlots != null && !substituteSlots.isEmpty()) {
            // Reassignment success: Reserve substitute helper's slots
            for (HelperAvailability slot : substituteSlots) {
                slot.setStatus(AvailabilityStatus.BOOKED);
            }
            availabilityRepository.saveAll(substituteSlots);

            Helper substituteHelper = substituteSlots.get(0).getHelper();
            booking.setAssignedHelperId(substituteHelper.getId());
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            task.setStatus(ReassignmentTaskStatus.REASSIGNED);
            task.setReassignedHelperId(substituteHelper.getId());
            task.setUpdatedAt(Instant.now(clock));
            taskRepository.save(task);

            eventPublisherService.publishEvent("BOOKING_REASSIGNED", "Booking",
                    booking.getId().toString(), substituteHelper.getId(), booking.getCustomer().getId(),
                    null, booking.getId(),
                    booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId(),
                    Map.of(
                            "bookingId", booking.getId(),
                            "originalHelperId", task.getOriginalHelperId(),
                            "newHelperId", substituteHelper.getId(),
                            "status", booking.getStatus().name()));
            log.info("Booking {} successfully reassigned to helper {}", booking.getId(), substituteHelper.getId());
        } else {
            // No substitute found: Graceful fallback cancellation with 100% refund
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            UUID seriesId = booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId();
            Optional<Payment> refund = paymentRecordService.createCancellationRefund(booking.getId(), seriesId, null);

            task.setStatus(ReassignmentTaskStatus.FAILED_CANCELLED);
            task.setFailureReason("No available substitute helper found for the requested slot.");
            task.setUpdatedAt(Instant.now(clock));
            taskRepository.save(task);

            eventPublisherService.publishEvent("BOOKING_CANCELLED_NO_HELPER_AVAILABLE", "Booking",
                    booking.getId().toString(), task.getOriginalHelperId(), booking.getCustomer().getId(),
                    refund.map(Payment::getId).orElse(null), booking.getId(), seriesId,
                    Map.of(
                            "bookingId", booking.getId(),
                            "originalHelperId", task.getOriginalHelperId(),
                            "refundId", refund.map(Payment::getId).orElse(null),
                            "reason", "No substitute helper available"));
            log.warn("No substitute helper found for booking {}; auto-cancelled with refund", booking.getId());
        }

        return task;
    }

    /**
     * Finds continuous available hourly slots for a substitute helper, excluding the original helper.
     */
    private List<HelperAvailability> findSubstituteSlots(Booking booking, UUID excludedHelperId) {
        long durationHours = Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        LocalTime firstSlotEnd = booking.getStartTime().plusHours(1);

        List<HelperAvailability> candidates = availabilityRepository.findAvailableHelpersForSlot(
                booking.getLocality(), booking.getSkill(), booking.getBookingDate(),
                booking.getStartTime(), firstSlotEnd, AvailabilityStatus.AVAILABLE);

        for (HelperAvailability candidate : candidates) {
            if (candidate.getHelper().getId().equals(excludedHelperId)) {
                continue;
            }

            List<HelperAvailability> slotsInWindow = availabilityRepository.findSlotsInWindow(
                    candidate.getHelper().getId(), booking.getBookingDate(),
                    booking.getStartTime(), booking.getEndTime(), AvailabilityStatus.AVAILABLE);

            if (slotsInWindow.size() == durationHours && isConsecutive(slotsInWindow, booking.getStartTime(), durationHours)) {
                return slotsInWindow;
            }
        }
        return null;
    }

    private boolean isConsecutive(List<HelperAvailability> slots, LocalTime startTime, long durationHours) {
        if (slots.size() != durationHours) {
            return false;
        }
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).getStartTime().equals(startTime.plusHours(i))) {
                return false;
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<ReassignmentTaskResponse> getAllTasks() {
        return taskRepository.findAll().stream().map(this::toResponse).toList();
    }

    private ReassignmentTaskResponse toResponse(ReassignmentTask task) {
        return ReassignmentTaskResponse.builder()
                .id(task.getId())
                .bookingId(task.getBookingId())
                .originalHelperId(task.getOriginalHelperId())
                .reassignedHelperId(task.getReassignedHelperId())
                .reason(task.getReason())
                .status(task.getStatus())
                .attempts(task.getAttempts())
                .failureReason(task.getFailureReason())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
