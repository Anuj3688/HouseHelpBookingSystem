package com.househelper.service;

import com.househelper.config.SearchProperties;
import com.househelper.dto.AvailabilityRequest;
import com.househelper.dto.EmergencyCancelResponse;
import com.househelper.dto.HelperEmergencyCancelRequest;
import com.househelper.dto.HelperOnboardRequest;
import com.househelper.dto.HelperRatingRequest;
import com.househelper.dto.HelperRatingResponse;
import com.househelper.dto.HelperSearchCriteria;
import com.househelper.dto.HelperSearchResponse;
import com.househelper.dto.HelperSingleBookingCancelRequest;
import com.househelper.dto.ReassignmentTaskResponse;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Booking;
import com.househelper.model.BookingStatus;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.ReassignmentTask;
import com.househelper.repository.BookingRepository;
import com.househelper.repository.HelperAvailabilityRepository;
import com.househelper.repository.HelperRepository;
import com.househelper.repository.HelperSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HelperService {

    private final HelperRepository helperRepository;
    private final HelperAvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final ReassignmentQueueService reassignmentQueueService;
    private final EventPublisherService eventPublisherService;
    private final SearchProperties searchProperties;
    private final Clock clock;

    @Transactional
    public HelperSearchResponse onboardHelper(HelperOnboardRequest request) {
        String phone = request.getPhone().trim();
        if (helperRepository.findByPhone(phone).isPresent()) {
            throw new ConflictException("A helper with this phone number is already registered.");
        }

        Helper helper = Helper.builder()
                .name(request.getName().trim())
                .phone(phone)
                .gender(request.getGender())
                .localities(normalizeLocalities(request))
                .skills(new HashSet<>(request.getSkills()))
                .hourlyRate(request.getHourlyRate())
                .governmentIdProof(request.getGovernmentIdProof())
                .build();
        Helper savedHelper = helperRepository.save(helper);
        return toResponse(savedHelper);
    }

    @Transactional
    public int updateAvailability(UUID helperId, List<AvailabilityRequest> requests) {
        Helper helper = helperRepository.findById(helperId)
                .orElseThrow(() -> new ResourceNotFoundException("Helper " + helperId + " was not found."));

        int updatedCount = 0;
        for (AvailabilityRequest request : requests) {
            validateAvailabilityRequest(request);

            HelperAvailability availability = availabilityRepository
                    .findByHelperIdAndSlotDateAndStartTime(helperId, request.getSlotDate(), request.getStartTime())
                    .orElseGet(() -> HelperAvailability.builder()
                            .helper(helper)
                            .slotDate(request.getSlotDate())
                            .startTime(request.getStartTime())
                            .build());

            // For Booking Cancellation we have another flow.
            if (availability.getStatus() == AvailabilityStatus.BOOKED) {
                log.error("A booked availability slot cannot be changed by helper availability updates. helperId={}, slotDate={}, startTime={}", helperId, request.getSlotDate(), request.getStartTime());
                throw new ConflictException("A booked availability slot cannot be changed by helper availability updates.");
            }

            availability.setEndTime(request.getEndTime());
            availability.setStatus(AvailabilityStatus.AVAILABLE);
            availabilityRepository.save(availability);
            updatedCount++;
        }
        eventPublisherService.publishEvent("HELPER_AVAILABILITY_UPDATED", "Helper",
                helperId.toString(), helperId, null, null, null,
                null, Map.of("helperId", helperId, "slotsUpdated", updatedCount, "slots", requests));
        return updatedCount;
    }

    @Transactional
    public ReassignmentTaskResponse cancelBookingByHelper(UUID helperId, UUID bookingId, HelperSingleBookingCancelRequest request) {
        Helper helper = helperRepository.findById(helperId)
                .orElseThrow(() -> new ResourceNotFoundException("Helper " + helperId + " was not found."));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " was not found."));

        if (!booking.getAssignedHelperId().equals(helperId)) {
            throw new InvalidRequestException("Booking " + bookingId + " is not assigned to helper " + helperId + ".");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking " + bookingId + " is already cancelled.");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ConflictException("Cannot cancel a booking that is already completed.");
        }

        // Mark helper's availability slots for this booking as NOT_AVAILABLE so helper cannot be re-booked
        List<HelperAvailability> slots = availabilityRepository.findSlotsInWindow(
                helperId, booking.getBookingDate(), booking.getStartTime(), booking.getEndTime(), AvailabilityStatus.BOOKED);
        for (HelperAvailability slot : slots) {
            slot.setStatus(AvailabilityStatus.NOT_AVAILABLE);
        }
        availabilityRepository.saveAll(slots);

        booking.setStatus(BookingStatus.PENDING_REASSIGNMENT);
        bookingRepository.save(booking);

        String reason = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim() : "Cancelled by helper";
        ReassignmentTask task = reassignmentQueueService.enqueueTask(bookingId, helperId, reason);

        eventPublisherService.publishEvent("HELPER_CANCELLED_BOOKING", "Booking",
                booking.getId().toString(), helperId, booking.getCustomer().getId(), null, booking.getId(),
                booking.getBookingSeries() == null ? null : booking.getBookingSeries().getId(),
                Map.of("bookingId", bookingId, "helperId", helperId, "reason", reason));

        reassignmentQueueService.triggerAsyncProcessing();

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

    @Transactional
    public EmergencyCancelResponse emergencyCancelAllBookings(UUID helperId, HelperEmergencyCancelRequest request) {
        Helper helper = helperRepository.findById(helperId)
                .orElseThrow(() -> new ResourceNotFoundException("Helper " + helperId + " was not found."));

        LocalDate today = LocalDate.now(clock);
        LocalDate fromDate = (request != null && request.getFromDate() != null) ? request.getFromDate() : today;
        LocalDate toDate = (request != null && request.getToDate() != null) ? request.getToDate() : today.plusYears(1);

        if (toDate.isBefore(fromDate)) {
            throw new InvalidRequestException("Emergency leave end date must not be before start date.");
        }

        // 1. Mark all helper availability slots in this date range as NOT_AVAILABLE
        List<HelperAvailability> slots = availabilityRepository.findByHelperIdAndSlotDateBetween(helperId, fromDate, toDate);
        for (HelperAvailability slot : slots) {
            slot.setStatus(AvailabilityStatus.NOT_AVAILABLE);
        }
        availabilityRepository.saveAll(slots);

        // 2. Find all active bookings for this helper in this date range
        List<Booking> activeBookings = bookingRepository.findByAssignedHelperIdAndStatusInAndBookingDateBetween(
                helperId, List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT), fromDate, toDate);

        List<UUID> cancelledBookingIds = new ArrayList<>();
        String reason = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim() : "Emergency mass cancellation by helper";

        for (Booking booking : activeBookings) {
            booking.setStatus(BookingStatus.PENDING_REASSIGNMENT);
            bookingRepository.save(booking);
            reassignmentQueueService.enqueueTask(booking.getId(), helperId, reason);
            cancelledBookingIds.add(booking.getId());
        }

        eventPublisherService.publishEvent("HELPER_EMERGENCY_MASS_CANCEL", "Helper",
                helperId.toString(), helperId, null, null, null, null,
                Map.of("helperId", helperId, "fromDate", fromDate, "toDate", toDate,
                        "cancelledBookingsCount", cancelledBookingIds.size(), "reason", reason));

        reassignmentQueueService.triggerAsyncProcessing();

        return EmergencyCancelResponse.builder()
                .helperId(helperId)
                .totalBookingsCancelled(cancelledBookingIds.size())
                .cancelledBookingIds(cancelledBookingIds)
                .tasksEnqueued(cancelledBookingIds.size())
                .message("Emergency leave applied. " + cancelledBookingIds.size() + " bookings marked for reassignment.")
                .build();
    }

    private void validateAvailabilityRequest(AvailabilityRequest request) {
        if (request == null || request.getSlotDate() == null || request.getStartTime() == null
                || request.getEndTime() == null || request.getStatus() == null) {
            throw new InvalidRequestException("Availability date, times, and status are required.");
        }

        if (request.getStatus() != AvailabilityStatus.AVAILABLE) {
            throw new InvalidRequestException(
                    "Availability updates may only set slots to AVAILABLE; booking workflows manage BOOKED status.");
        }

        if (request.getSlotDate().isBefore(LocalDate.now(clock))) {
            throw new InvalidRequestException("Availability cannot be added for a past date.");
        }

        if (request.getStartTime().getMinute() != 0
                || request.getStartTime().getSecond() != 0
                || request.getStartTime().getNano() != 0
                || !request.getEndTime().equals(request.getStartTime().plusHours(1))
                || !request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidRequestException(
                    "Availability must use one-hour slots starting on the hour, such as 09:00-10:00.");
        }
    }

    @Transactional(readOnly = true)
    public Page<HelperSearchResponse> searchHelpers(HelperSearchCriteria criteria) {
        if (criteria.getPage() < 0 || criteria.getSize() < 1
                || criteria.getSize() > searchProperties.getMaxPageSize()) {
            throw new InvalidRequestException("Page must be non-negative and size must be between 1 and "
                    + searchProperties.getMaxPageSize() + ".");
        }
        if (!criteria.getEndTime().isAfter(criteria.getStartTime())) {
            throw new InvalidRequestException("Search end time must be later than start time.");
        }
        if (criteria.getStartTime().getMinute() != 0 || criteria.getStartTime().getSecond() != 0
                || criteria.getEndTime().getMinute() != 0 || criteria.getEndTime().getSecond() != 0) {
            throw new InvalidRequestException(
                    "Search times must start and end on the hour (e.g. 09:00, 10:00). Fractional durations such as 15 or 30 minutes are not permitted.");
        }

        PageRequest pageable = PageRequest.of(criteria.getPage(), criteria.getSize());
        Page<HelperSearchResponse> results = helperRepository
                .findAll(HelperSpecifications.matching(criteria), pageable)
                .map(this::toResponse);
        log.debug("Helper search completed: skill={}, page={}, size={}, results={}",
                criteria.getSkill(), criteria.getPage(), criteria.getSize(), results.getNumberOfElements());
        return results;
    }

    @Transactional
    public HelperRatingResponse addRating(UUID helperId, HelperRatingRequest request) {
        Helper helper = helperRepository.findByIdForUpdate(helperId)
                .orElseThrow(() -> new ResourceNotFoundException("Helper " + helperId + " was not found."));

        helper.setTotalRating(helper.getTotalRating().add(BigDecimal.valueOf(request.getRating())));
        helper.setRatingCount(helper.getRatingCount() + 1);
        helperRepository.save(helper);

        return HelperRatingResponse.builder()
                .helperId(helper.getId())
                .rating(helper.getRating())
                .ratingCount(helper.getRatingCount())
                .build();
    }

    private HashSet<String> normalizeLocalities(HelperOnboardRequest request) {
        HashSet<String> localities = new HashSet<>();
        for (String locality : request.getLocalities()) {
            String normalized = locality.trim();
            if (normalized.isEmpty()) {
                throw new InvalidRequestException("Localities must not be blank.");
            }
            localities.add(normalized);
        }
        if (localities.size() > 3) {
            throw new InvalidRequestException("A helper can serve at most three localities.");
        }
        return localities;
    }

    private HelperSearchResponse toResponse(Helper helper) {
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
}
