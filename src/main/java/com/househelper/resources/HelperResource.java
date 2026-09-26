package com.househelper.resources;

import com.househelper.dto.AvailabilityRequest;
import com.househelper.dto.HelperOnboardRequest;
import com.househelper.dto.HelperSearchResponse;
import com.househelper.model.SkillType;
import com.househelper.service.HelperService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/helpers")
@RequiredArgsConstructor
@Tag(name = "Helpers", description = "Register helpers, manage availability, and search by service criteria.")
public class HelperResource {

    private final HelperService helperService;

    @PostMapping
    @Operation(summary = "Register a helper", description = "Creates a helper profile with supported localities, skills, and hourly rate.")
    public HelperSearchResponse onboardHelper(@Valid @RequestBody HelperOnboardRequest request) {
        return helperService.onboardHelper(request);
    }

    @PutMapping("/{helperId}/availability")
    @Operation(summary = "Set helper availability", description = "Creates or updates the helper's date and time slots.")
    public Map<String, Object> updateAvailability(
            @PathVariable Long helperId,
            @Valid @RequestBody List<@Valid AvailabilityRequest> requests) {
        int savedSlots = helperService.updateAvailability(helperId, requests);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("helperId", helperId);
        response.put("slotsUpdated", savedSlots);
        return response;
    }

    @GetMapping
    @Operation(summary = "Search helpers", description = "Finds helpers by locality and skill, ordered by hourly rate and rating.")
    public Page<HelperSearchResponse> searchHelpers(
            @RequestParam String locality,
            @RequestParam SkillType skill,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return helperService.searchHelpers(locality, skill, page, size);
    }
}
