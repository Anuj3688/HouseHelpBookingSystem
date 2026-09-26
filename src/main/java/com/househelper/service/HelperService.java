package com.househelper.service;

import com.househelper.dto.AvailabilityRequest;
import com.househelper.dto.HelperOnboardRequest;
import com.househelper.dto.HelperRatingRequest;
import com.househelper.dto.HelperRatingResponse;
import com.househelper.dto.HelperSearchCriteria;
import com.househelper.dto.HelperSearchResponse;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
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
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HelperService {

    private static final int MAX_PAGE_SIZE = 100;

    private final HelperRepository helperRepository;
    private final HelperAvailabilityRepository availabilityRepository;

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
    public int updateAvailability(Long helperId, List<AvailabilityRequest> requests) {
        Helper helper = helperRepository.findById(helperId)
                .orElseThrow(() -> new ResourceNotFoundException("Helper " + helperId + " was not found."));

        int updatedCount = 0;
        for (AvailabilityRequest request : requests) {
            if (!request.getEndTime().isAfter(request.getStartTime())) {
                throw new InvalidRequestException("Availability end time must be later than start time.");
            }
            if (request.getStatus() != AvailabilityStatus.AVAILABLE) {
                throw new InvalidRequestException(
                        "Availability updates may only set slots to AVAILABLE; booking workflows manage BOOKED status.");
            }
            if (availabilityRepository.existsOverlappingAvailability(helperId, request.getSlotDate(),
                    request.getStartTime(), request.getStartTime(), request.getEndTime())) {
                throw new ConflictException("Availability slots for a helper cannot overlap.");
            }

            HelperAvailability availability = availabilityRepository
                    .findByHelperIdAndSlotDateAndStartTime(helperId, request.getSlotDate(), request.getStartTime())
                    .orElseGet(() -> HelperAvailability.builder()
                            .helper(helper)
                            .slotDate(request.getSlotDate())
                            .startTime(request.getStartTime())
                            .build());
            if (availability.getStatus() == AvailabilityStatus.BOOKED) {
                throw new ConflictException("A booked availability slot cannot be changed by helper availability updates.");
            }
            availability.setEndTime(request.getEndTime());
            availability.setStatus(AvailabilityStatus.AVAILABLE);
            availabilityRepository.save(availability);
            updatedCount++;
        }
        log.info("Updated {} availability slots for helperId={}", updatedCount, helperId);
        return updatedCount;
    }

    @Transactional(readOnly = true)
    public Page<HelperSearchResponse> searchHelpers(HelperSearchCriteria criteria) {
        if (criteria.getPage() < 0 || criteria.getSize() < 1 || criteria.getSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Page must be non-negative and size must be between 1 and 100.");
        }
        if (!criteria.getEndTime().isAfter(criteria.getStartTime())) {
            throw new InvalidRequestException("Search end time must be later than start time.");
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
    public HelperRatingResponse addRating(Long helperId, HelperRatingRequest request) {
        Helper helper = helperRepository.findByIdForUpdate(helperId)
                .orElseThrow(() -> new ResourceNotFoundException("Helper " + helperId + " was not found."));

        helper.setTotalRating(helper.getTotalRating().add(request.getRating()));
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
