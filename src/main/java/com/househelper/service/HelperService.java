package com.househelper.service;

import com.househelper.dto.AvailabilityRequest;
import com.househelper.dto.HelperOnboardRequest;
import com.househelper.dto.HelperSearchResponse;
import com.househelper.exception.ConflictException;
import com.househelper.exception.InvalidRequestException;
import com.househelper.exception.ResourceNotFoundException;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.SkillType;
import com.househelper.repository.HelperAvailabilityRepository;
import com.househelper.repository.HelperRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .localities(normalizeLocalities(request))
                .skills(new HashSet<>(request.getSkills()))
                .hourlyRate(request.getHourlyRate())
                .rating(0.0)
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
            availability.setEndTime(request.getEndTime());
            availability.setStatus(request.getStatus());
            availabilityRepository.save(availability);
            updatedCount++;
        }
        log.info("Updated {} availability slots for helperId={}", updatedCount, helperId);
        return updatedCount;
    }

    @Transactional(readOnly = true)
    public Page<HelperSearchResponse> searchHelpers(String locality, SkillType skill,
                                                    int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Page must be non-negative and size must be between 1 and 100.");
        }
        if (locality == null || locality.isBlank()) {
            throw new InvalidRequestException("Locality is required.");
        }
        if (skill == null) {
            throw new InvalidRequestException("Skill is required.");
        }

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("hourlyRate"), Sort.Order.desc("rating")));
        Page<HelperSearchResponse> results = helperRepository
                .searchHelpers(locality.trim(), skill, pageable)
                .map(this::toResponse);
        log.debug("Helper search completed: skill={}, page={}, size={}, results={}",
                skill, page, size, results.getNumberOfElements());
        return results;
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
                .localities(new HashSet<>(helper.getLocalities()))
                .skills(new HashSet<>(helper.getSkills()))
                .hourlyRate(helper.getHourlyRate())
                .rating(helper.getRating())
                .build();
    }
}
