package com.househelper.resources;

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
import com.househelper.service.HelperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            @PathVariable UUID helperId,
            @Valid @RequestBody List<@Valid AvailabilityRequest> requests) {
        int savedSlots = helperService.updateAvailability(helperId, requests);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("helperId", helperId);
        response.put("slotsUpdated", savedSlots);
        return response;
    }

    @PostMapping("/{helperId}/ratings")
    @Operation(summary = "Rate a helper", description = "Adds one rating from 1 to 5 and returns the updated average and rating count.")
    public HelperRatingResponse addRating(@PathVariable UUID helperId,
                                          @Valid @RequestBody HelperRatingRequest request) {
        return helperService.addRating(helperId, request);
    }

    @GetMapping
    @Operation(summary = "Search available helpers", description = "Finds helpers available for the requested date and time, with optional gender, price, and rating filters.")
    public Page<HelperSearchResponse> searchHelpers(
            @Valid @ModelAttribute HelperSearchCriteria criteria) {
        return helperService.searchHelpers(criteria);
    }

    @PostMapping("/{helperId}/bookings/{bookingId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Cancel a specific booking by helper",
            description = "Cancels a specific booking by the assigned helper, frees the slot from the helper, and queues for substitute reassignment.")
    public ReassignmentTaskResponse cancelBookingByHelper(
            @PathVariable UUID helperId,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) HelperSingleBookingCancelRequest request) {
        return helperService.cancelBookingByHelper(helperId, bookingId, request);
    }

    @PostMapping("/{helperId}/emergency-cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Emergency mass cancellation by helper",
            description = "Cancels all active bookings for the helper across the emergency leave period, marks helper slots as NOT_AVAILABLE, and queues all bookings for seamless substitute reassignment.")
    public EmergencyCancelResponse emergencyCancelAllBookings(
            @PathVariable UUID helperId,
            @RequestBody(required = false) HelperEmergencyCancelRequest request) {
        return helperService.emergencyCancelAllBookings(helperId, request);
    }
}
