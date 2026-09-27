package com.househelper.service;

import com.househelper.config.SearchProperties;
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
import com.househelper.model.Gender;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.SkillType;
import com.househelper.repository.HelperAvailabilityRepository;
import com.househelper.repository.HelperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelperServiceTest {

    private static UUID uuid(long value) {
        return UUID.nameUUIDFromBytes(("test-id-" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final UUID HELPER_ID = uuid(25);
    private static final LocalDate SLOT_DATE = LocalDate.of(2026, 10, 5);
    private static final LocalTime SLOT_START = LocalTime.of(9, 0);
    private static final LocalTime SLOT_END = LocalTime.of(10, 0);

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private HelperAvailabilityRepository availabilityRepository;

    @Mock
    private EventPublisherService eventPublisherService;

    private SearchProperties searchProperties;

    private HelperService helperService;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.setMaxPageSize(100);
        helperService = new HelperService(helperRepository, availabilityRepository,
                eventPublisherService, searchProperties,
                Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Onboards a helper with trimmed identity fields and normalized localities")
    void onboardHelper() {
        HelperOnboardRequest request = onboardRequest(Set.of(" Central ", "East "), Set.of(SkillType.CLEANING));
        when(helperRepository.findByPhone("5550101")).thenReturn(Optional.empty());
        when(helperRepository.save(any(Helper.class))).thenAnswer(invocation -> {
            Helper saved = invocation.getArgument(0);
            saved.setId(HELPER_ID);
            return saved;
        });

        HelperSearchResponse response = helperService.onboardHelper(request);

        assertEquals(HELPER_ID, response.getId());
        assertEquals("Helper Name", response.getName());
        assertEquals(Set.of("Central", "East"), response.getLocalities());
        assertEquals(Set.of(SkillType.CLEANING), response.getSkills());
        assertEquals(0.0, response.getRating());
        ArgumentCaptor<Helper> helperCaptor = ArgumentCaptor.forClass(Helper.class);
        verify(helperRepository).save(helperCaptor.capture());
        assertEquals("5550101", helperCaptor.getValue().getPhone());
    }

    @Test
    @DisplayName("Rejects onboarding a helper whose phone number is already registered")
    void onboardDuplicatePhone() {
        when(helperRepository.findByPhone("5550101")).thenReturn(Optional.of(helper(HELPER_ID)));

        assertThrows(ConflictException.class, () -> helperService.onboardHelper(
                onboardRequest(Set.of("Central"), Set.of(SkillType.CLEANING))));

        verify(helperRepository, never()).save(any(Helper.class));
        verifyNoInteractions(availabilityRepository, eventPublisherService);
    }

    @Test
    @DisplayName("Rejects a blank locality after trimming helper locality values")
    void onboardBlankLocality() {
        when(helperRepository.findByPhone("5550101")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class, () -> helperService.onboardHelper(
                onboardRequest(Set.of("   "), Set.of(SkillType.CLEANING))));

        verify(helperRepository, never()).save(any(Helper.class));
    }

    @Test
    @DisplayName("Rejects a helper with more than three distinct normalized localities")
    void onboardTooManyLocalities() {
        when(helperRepository.findByPhone("5550101")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class, () -> helperService.onboardHelper(
                onboardRequest(Set.of("A", "B", "C", "D"), Set.of(SkillType.CLEANING))));

        verify(helperRepository, never()).save(any(Helper.class));
    }

    @Test
    @DisplayName("Adds an available hourly slot and publishes the availability audit event")
    void updateAvailability() {
        Helper helper = helper(HELPER_ID);
        AvailabilityRequest request = availabilityRequest(
                SLOT_DATE, SLOT_START, SLOT_END, AvailabilityStatus.AVAILABLE);
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.of(helper));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(
                HELPER_ID, SLOT_DATE, SLOT_START)).thenReturn(Optional.empty());

        int updatedCount = helperService.updateAvailability(HELPER_ID, List.of(request));

        assertEquals(1, updatedCount);
        verify(availabilityRepository).save(any(HelperAvailability.class));
        verify(eventPublisherService).publishEvent(
                eq("HELPER_AVAILABILITY_UPDATED"), eq("Helper"), eq(HELPER_ID.toString()),
                eq(HELPER_ID), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("Rejects availability updates for a helper that does not exist")
    void updateAvailabilityMissingHelper() {
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> helperService.updateAvailability(HELPER_ID, List.of()));

        verifyNoInteractions(availabilityRepository, eventPublisherService);
    }

    @Test
    @DisplayName("Rejects attempts to overwrite a slot already booked by a customer")
    void updateBookedAvailability() {
        Helper helper = helper(HELPER_ID);
        HelperAvailability bookedSlot = HelperAvailability.builder()
                .helper(helper)
                .slotDate(SLOT_DATE)
                .startTime(SLOT_START)
                .endTime(SLOT_END)
                .status(AvailabilityStatus.BOOKED)
                .build();
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.of(helper));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(
                HELPER_ID, SLOT_DATE, SLOT_START)).thenReturn(Optional.of(bookedSlot));

        assertThrows(ConflictException.class, () -> helperService.updateAvailability(
                HELPER_ID, List.of(availabilityRequest(
                        SLOT_DATE, SLOT_START, SLOT_END, AvailabilityStatus.AVAILABLE))));

        verify(availabilityRepository, never()).save(any(HelperAvailability.class));
        verifyNoInteractions(eventPublisherService);
    }

    @Test
    @DisplayName("Rejects helper availability with a status other than available")
    void updateAvailabilityInvalidStatus() {
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.of(helper(HELPER_ID)));

        assertThrows(InvalidRequestException.class, () -> helperService.updateAvailability(
                HELPER_ID, List.of(availabilityRequest(
                        SLOT_DATE, SLOT_START, SLOT_END, AvailabilityStatus.NOT_AVAILABLE))));

        verify(availabilityRepository, never()).save(any(HelperAvailability.class));
        verifyNoInteractions(eventPublisherService);
    }

    @Test
    @DisplayName("Rejects availability dates in the past without saving a slot")
    void updateAvailabilityPastDate() {
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.of(helper(HELPER_ID)));

        assertThrows(InvalidRequestException.class, () -> helperService.updateAvailability(
                HELPER_ID, List.of(availabilityRequest(
                        LocalDate.of(2026, 9, 26), SLOT_START, SLOT_END, AvailabilityStatus.AVAILABLE))));

        verify(availabilityRepository, never()).save(any(HelperAvailability.class));
        verifyNoInteractions(eventPublisherService);
    }

    @Test
    @DisplayName("Allows availability for the current date")
    void updateAvailabilityCurrentDate() {
        LocalDate currentDate = LocalDate.now(Clock.fixed(
                Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));
        Helper helper = helper(HELPER_ID);
        AvailabilityRequest request = availabilityRequest(
                currentDate, SLOT_START, SLOT_END, AvailabilityStatus.AVAILABLE);
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.of(helper));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(
                HELPER_ID, currentDate, SLOT_START)).thenReturn(Optional.empty());

        int updatedCount = helperService.updateAvailability(HELPER_ID, List.of(request));

        assertEquals(1, updatedCount);
        verify(availabilityRepository).save(any(HelperAvailability.class));
    }

    @Test
    @DisplayName("Allows availability for a future date")
    void updateAvailabilityFutureDate() {
        LocalDate futureDate = LocalDate.of(2026, 9, 28);
        Helper helper = helper(HELPER_ID);
        AvailabilityRequest request = availabilityRequest(
                futureDate, SLOT_START, SLOT_END, AvailabilityStatus.AVAILABLE);
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.of(helper));
        when(availabilityRepository.findByHelperIdAndSlotDateAndStartTime(
                HELPER_ID, futureDate, SLOT_START)).thenReturn(Optional.empty());

        int updatedCount = helperService.updateAvailability(HELPER_ID, List.of(request));

        assertEquals(1, updatedCount);
        verify(availabilityRepository).save(any(HelperAvailability.class));
    }

    @Test
    @DisplayName("Rejects an availability request with a missing date without throwing a null error")
    void updateAvailabilityMissingDate() {
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.of(helper(HELPER_ID)));

        assertThrows(InvalidRequestException.class, () -> helperService.updateAvailability(
                HELPER_ID, List.of(availabilityRequest(null, SLOT_START, SLOT_END, AvailabilityStatus.AVAILABLE))));

        verify(availabilityRepository, never()).save(any(HelperAvailability.class));
        verifyNoInteractions(eventPublisherService);
    }

    @Test
    @DisplayName("Rejects availability that is not one hour with an on-the-hour start")
    void updateAvailabilityInvalidDuration() {
        when(helperRepository.findById(HELPER_ID)).thenReturn(Optional.of(helper(HELPER_ID)));

        assertThrows(InvalidRequestException.class, () -> helperService.updateAvailability(
                HELPER_ID, List.of(availabilityRequest(
                        SLOT_DATE, LocalTime.of(9, 30), LocalTime.of(10, 30), AvailabilityStatus.AVAILABLE))));

        verify(availabilityRepository, never()).save(any(HelperAvailability.class));
        verifyNoInteractions(eventPublisherService);
    }

    @Test
    @DisplayName("Returns matching helpers with the requested page and normalized rating data")
    void searchHelpers() {
        HelperSearchCriteria criteria = searchCriteria(0, 10, SLOT_START, SLOT_END);
        Helper helper = helper(HELPER_ID);
        when(helperRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Helper>>any(), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(helper), PageRequest.of(0, 10), 1));

        Page<HelperSearchResponse> results = helperService.searchHelpers(criteria);

        assertEquals(1, results.getTotalElements());
        assertEquals(HELPER_ID, results.getContent().getFirst().getId());
        assertEquals(4.5, results.getContent().getFirst().getRating());
        assertEquals(2L, results.getContent().getFirst().getRatingCount());
        verify(helperRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Helper>>any(), eq(PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("Rejects helper searches whose requested page size exceeds the configured limit")
    void searchHelpersTooLarge() {
        HelperSearchCriteria criteria = searchCriteria(0, 101, SLOT_START, SLOT_END);

        assertThrows(InvalidRequestException.class, () -> helperService.searchHelpers(criteria));

        verifyNoInteractions(helperRepository);
    }

    @Test
    @DisplayName("Rejects helper searches whose end time is not after their start time")
    void searchHelpersInvalidPeriod() {
        HelperSearchCriteria criteria = searchCriteria(0, 10, SLOT_START, SLOT_START);

        assertThrows(InvalidRequestException.class, () -> helperService.searchHelpers(criteria));

        verifyNoInteractions(helperRepository);
    }

    @Test
    @DisplayName("Adds a helper rating under a row lock and returns the updated average")
    void addRating() {
        Helper helper = helper(HELPER_ID);
        HelperRatingRequest request = ratingRequest(5);
        when(helperRepository.findByIdForUpdate(HELPER_ID)).thenReturn(Optional.of(helper));
        when(helperRepository.save(helper)).thenReturn(helper);

        HelperRatingResponse response = helperService.addRating(HELPER_ID, request);

        assertEquals(HELPER_ID, response.getHelperId());
        assertEquals(14.0 / 3.0, response.getRating(), 0.01);
        assertEquals(3L, response.getRatingCount());
        verify(helperRepository).save(helper);
        verifyNoInteractions(availabilityRepository, eventPublisherService);
    }

    @Test
    @DisplayName("Reports a missing helper when adding a rating")
    void addRatingMissingHelper() {
        when(helperRepository.findByIdForUpdate(HELPER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> helperService.addRating(HELPER_ID, ratingRequest(5)));

        verify(helperRepository, never()).save(any(Helper.class));
        verifyNoInteractions(availabilityRepository, eventPublisherService);
    }

    private HelperOnboardRequest onboardRequest(Set<String> localities, Set<SkillType> skills) {
        return HelperOnboardRequest.builder()
                .name(" Helper Name ")
                .phone(" 5550101 ")
                .gender(Gender.FEMALE)
                .localities(localities)
                .skills(skills)
                .hourlyRate(350.0)
                .governmentIdProof("encrypted-proof-input")
                .build();
    }

    private AvailabilityRequest availabilityRequest(LocalDate date,
                                                    LocalTime start,
                                                    LocalTime end,
                                                    AvailabilityStatus status) {
        return AvailabilityRequest.builder()
                .slotDate(date)
                .startTime(start)
                .endTime(end)
                .status(status)
                .build();
    }

    private HelperSearchCriteria searchCriteria(int page, int size, LocalTime start, LocalTime end) {
        HelperSearchCriteria criteria = new HelperSearchCriteria();
        criteria.setLocality("Central");
        criteria.setSkill(SkillType.CLEANING);
        criteria.setDate(SLOT_DATE);
        criteria.setStartTime(start);
        criteria.setEndTime(end);
        criteria.setPage(page);
        criteria.setSize(size);
        return criteria;
    }

    private HelperRatingRequest ratingRequest(int rating) {
        HelperRatingRequest request = new HelperRatingRequest();
        request.setRating(rating);
        return request;
    }

    private Helper helper(UUID id) {
        return Helper.builder()
                .id(id)
                .name("Helper Name")
                .phone("5550101")
                .gender(Gender.FEMALE)
                .localities(Set.of("Central", "East"))
                .skills(Set.of(SkillType.CLEANING))
                .hourlyRate(350.0)
                .totalRating(BigDecimal.valueOf(9))
                .ratingCount(2L)
                .governmentIdProof("encrypted-proof")
                .build();
    }
}
