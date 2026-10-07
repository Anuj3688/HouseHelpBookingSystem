package com.househelper.repository;

import com.househelper.dto.HelperSearchCriteria;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Gender;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.SkillType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HelperSpecificationsTest {

    @Mock
    private Root<Helper> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Predicate conjunctionPredicate;

    @BeforeEach
    void setUp() {
        when(cb.conjunction()).thenReturn(conjunctionPredicate);
    }

    @Test
    @DisplayName("hasLocality returns conjunction if locality is null or blank")
    void hasLocalityNullOrBlank() {
        Specification<Helper> specNull = HelperSpecifications.hasLocality(null);
        Predicate resultNull = specNull.toPredicate(root, query, cb);
        assertSame(conjunctionPredicate, resultNull);

        Specification<Helper> specBlank = HelperSpecifications.hasLocality("   ");
        Predicate resultBlank = specBlank.toPredicate(root, query, cb);
        assertSame(conjunctionPredicate, resultBlank);
    }

    @Test
    @DisplayName("hasLocality generates exists subquery for matching locality")
    @SuppressWarnings("unchecked")
    void hasLocalityValid() {
        Subquery<Integer> subquery = mock(Subquery.class);
        Root<Helper> subRoot = mock(Root.class);
        Join<Helper, String> join = mock(Join.class);
        Path<Object> helperIdPath = mock(Path.class);
        Path<Object> rootIdPath = mock(Path.class);
        Predicate existsPredicate = mock(Predicate.class);

        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(Helper.class)).thenReturn(subRoot);
        when(subRoot.<Helper, String>join("localities")).thenReturn(join);
        when(subRoot.get("id")).thenReturn(helperIdPath);
        when(root.get("id")).thenReturn(rootIdPath);
        when(subquery.select(any())).thenReturn(subquery);
        when(cb.exists(subquery)).thenReturn(existsPredicate);

        Specification<Helper> spec = HelperSpecifications.hasLocality("Central");
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(existsPredicate, result);
        verify(cb).exists(subquery);
    }

    @Test
    @DisplayName("hasSkill returns conjunction if skill is null")
    void hasSkillNull() {
        Specification<Helper> spec = HelperSpecifications.hasSkill(null);
        Predicate result = spec.toPredicate(root, query, cb);
        assertSame(conjunctionPredicate, result);
    }

    @Test
    @DisplayName("hasSkill generates exists subquery for matching skill")
    @SuppressWarnings("unchecked")
    void hasSkillValid() {
        Subquery<Integer> subquery = mock(Subquery.class);
        Root<Helper> subRoot = mock(Root.class);
        Join<Helper, Object> join = mock(Join.class);
        Path<Object> helperIdPath = mock(Path.class);
        Path<Object> rootIdPath = mock(Path.class);
        Predicate existsPredicate = mock(Predicate.class);

        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(Helper.class)).thenReturn(subRoot);
        when(subRoot.<Helper, Object>join("skills")).thenReturn(join);
        when(subRoot.get("id")).thenReturn(helperIdPath);
        when(root.get("id")).thenReturn(rootIdPath);
        when(subquery.select(any())).thenReturn(subquery);
        when(cb.exists(subquery)).thenReturn(existsPredicate);

        Specification<Helper> spec = HelperSpecifications.hasSkill(SkillType.CLEANING);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(existsPredicate, result);
        verify(cb).exists(subquery);
    }

    @Test
    @DisplayName("hasGender returns conjunction if gender is null")
    void hasGenderNull() {
        Specification<Helper> spec = HelperSpecifications.hasGender(null);
        Predicate result = spec.toPredicate(root, query, cb);
        assertSame(conjunctionPredicate, result);
    }

    @Test
    @DisplayName("hasGender generates equality predicate for gender")
    @SuppressWarnings("unchecked")
    void hasGenderValid() {
        Path<Gender> genderPath = mock(Path.class);
        Predicate equalPredicate = mock(Predicate.class);

        when(root.<Gender>get("gender")).thenReturn(genderPath);
        when(cb.equal(genderPath, Gender.FEMALE)).thenReturn(equalPredicate);

        Specification<Helper> spec = HelperSpecifications.hasGender(Gender.FEMALE);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(equalPredicate, result);
        verify(cb).equal(genderPath, Gender.FEMALE);
    }

    @Test
    @DisplayName("hasMaxHourlyRate returns conjunction if rate is null")
    void hasMaxHourlyRateNull() {
        Specification<Helper> spec = HelperSpecifications.hasMaxHourlyRate(null);
        Predicate result = spec.toPredicate(root, query, cb);
        assertSame(conjunctionPredicate, result);
    }

    @Test
    @DisplayName("hasMaxHourlyRate generates lessThanOrEqualTo predicate")
    @SuppressWarnings("unchecked")
    void hasMaxHourlyRateValid() {
        Path<Double> hourlyRatePath = mock(Path.class);
        Predicate lePredicate = mock(Predicate.class);

        when(root.<Double>get("hourlyRate")).thenReturn(hourlyRatePath);
        when(cb.lessThanOrEqualTo(hourlyRatePath, 400.0)).thenReturn(lePredicate);

        Specification<Helper> spec = HelperSpecifications.hasMaxHourlyRate(400.0);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(lePredicate, result);
        verify(cb).lessThanOrEqualTo(hourlyRatePath, 400.0);
    }

    @Test
    @DisplayName("hasMinRating returns conjunction if minRating is null")
    void hasMinRatingNull() {
        Specification<Helper> spec = HelperSpecifications.hasMinRating(null);
        Predicate result = spec.toPredicate(root, query, cb);
        assertSame(conjunctionPredicate, result);
    }

    @Test
    @DisplayName("hasContinuousAvailableSlots returns conjunction if date or times are null")
    void hasContinuousAvailableSlotsNull() {
        Specification<Helper> spec = HelperSpecifications.hasContinuousAvailableSlots(null, null, null);
        Predicate result = spec.toPredicate(root, query, cb);
        assertSame(conjunctionPredicate, result);
    }

    @Test
    @DisplayName("hasContinuousAvailableSlots builds subquery matching exact duration hours")
    @SuppressWarnings("unchecked")
    void hasContinuousAvailableSlotsValid() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(12, 0); // 3 hours

        Subquery<Long> subquery = mock(Subquery.class);
        Root<HelperAvailability> slotRoot = mock(Root.class);
        Path<Helper> helperPath = mock(Path.class);
        Path<Object> helperIdPath = mock(Path.class);
        Path<Object> rootIdPath = mock(Path.class);
        Path<LocalDate> slotDatePath = mock(Path.class);
        Path<LocalTime> startTimePath = mock(Path.class);
        Path<LocalTime> endTimePath = mock(Path.class);
        Path<AvailabilityStatus> statusPath = mock(Path.class);
        Path<Object> slotIdPath = mock(Path.class);
        Expression<Long> countExpr = mock(Expression.class);
        Predicate equalCount = mock(Predicate.class);

        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(HelperAvailability.class)).thenReturn(slotRoot);
        when(slotRoot.<Helper>get("helper")).thenReturn(helperPath);
        when(helperPath.get("id")).thenReturn(helperIdPath);
        when(root.get("id")).thenReturn(rootIdPath);
        when(slotRoot.<LocalDate>get("slotDate")).thenReturn(slotDatePath);
        when(slotRoot.<LocalTime>get("startTime")).thenReturn(startTimePath);
        when(slotRoot.<LocalTime>get("endTime")).thenReturn(endTimePath);
        when(slotRoot.<AvailabilityStatus>get("status")).thenReturn(statusPath);
        when(slotRoot.get("id")).thenReturn(slotIdPath);
        when(cb.count(slotIdPath)).thenReturn(countExpr);
        when(subquery.select(countExpr)).thenReturn(subquery);
        when(cb.equal(subquery, 3L)).thenReturn(equalCount);

        Specification<Helper> spec = HelperSpecifications.hasContinuousAvailableSlots(date, start, end);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(equalCount, result);
        verify(cb).equal(subquery, 3L);
    }

    @Test
    @DisplayName("withOrdering sets query orderBy clauses by rating desc and hourlyRate asc")
    @SuppressWarnings("unchecked")
    void withOrderingValid() {
        Path<Double> hourlyRatePath = mock(Path.class);
        Path<Object> totalRatingPath = mock(Path.class);
        Path<Long> ratingCountPath = mock(Path.class);
        Order descRating = mock(Order.class);
        Order ascRate = mock(Order.class);
        CriteriaBuilder.Case<Double> selectCase = mock(CriteriaBuilder.Case.class);

        when(root.<Double>get("hourlyRate")).thenReturn(hourlyRatePath);
        when(root.get("totalRating")).thenReturn(totalRatingPath);
        when(root.<Long>get("ratingCount")).thenReturn(ratingCountPath);
        doReturn(Object.class).when(query).getResultType();

        when(cb.<Double>selectCase()).thenReturn(selectCase);
        when(selectCase.when(any(), ArgumentMatchers.<Expression<? extends Double>>any())).thenReturn(selectCase);
        when(selectCase.otherwise(ArgumentMatchers.<Double>any())).thenReturn(mock(Expression.class));

        when(cb.asc(hourlyRatePath)).thenReturn(ascRate);
        when(cb.desc(any())).thenReturn(descRating);

        Specification<Helper> spec = HelperSpecifications.withOrdering();
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(conjunctionPredicate, result);
        verify(query).orderBy(ascRate, descRating);
    }

    @Test
    @DisplayName("matching composes criteria specifications properly")
    void matchingComposition() {
        HelperSearchCriteria criteria = new HelperSearchCriteria();
        criteria.setLocality("Central");
        criteria.setSkill(SkillType.CLEANING);
        criteria.setGender(Gender.MALE);
        criteria.setMaxHourlyRate(500.0);
        criteria.setMinRating(4.0);
        criteria.setDate(LocalDate.of(2026, 10, 5));
        criteria.setStartTime(LocalTime.of(9, 0));
        criteria.setEndTime(LocalTime.of(10, 0));

        Specification<Helper> compositeSpec = HelperSpecifications.matching(criteria);
        assertNotNull(compositeSpec);
    }
}
