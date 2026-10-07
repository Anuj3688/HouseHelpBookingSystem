package com.househelper.repository;

import com.househelper.dto.HelperSearchCriteria;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Gender;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import com.househelper.model.SkillType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

/**
 * Reusable and composable JPA Specifications for filtering Helpers.
 * Adheres to the Open-Closed Principle allowing search filters to be plugged
 * or combined freely without modifying core matching logic.
 */
public final class HelperSpecifications {

    private HelperSpecifications() {
    }

    /**
     * Filter helpers that serve the specified locality (case-insensitive).
     */
    public static Specification<Helper> hasLocality(String locality) {
        if (locality == null || locality.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, builder) -> {
            Subquery<Integer> localityMatch = query.subquery(Integer.class);
            Root<Helper> localityHelper = localityMatch.from(Helper.class);
            Join<Helper, String> localityJoin = localityHelper.join("localities");
            localityMatch.select(builder.literal(1))
                    .where(
                            builder.equal(localityHelper.get("id"), root.get("id")),
                            builder.equal(builder.lower(localityJoin),
                                    locality.trim().toLowerCase(Locale.ROOT)));
            return builder.exists(localityMatch);
        };
    }

    /**
     * Filter helpers that offer the specified skill.
     */
    public static Specification<Helper> hasSkill(SkillType skill) {
        if (skill == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, builder) -> {
            Subquery<Integer> skillMatch = query.subquery(Integer.class);
            Root<Helper> skillHelper = skillMatch.from(Helper.class);
            Join<Helper, ?> skillJoin = skillHelper.join("skills");
            skillMatch.select(builder.literal(1))
                    .where(
                            builder.equal(skillHelper.get("id"), root.get("id")),
                            builder.equal(skillJoin, skill));
            return builder.exists(skillMatch);
        };
    }

    /**
     * Filter helpers by gender.
     */
    public static Specification<Helper> hasGender(Gender gender) {
        if (gender == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, builder) -> builder.equal(root.get("gender"), gender);
    }

    /**
     * Filter helpers whose hourly rate does not exceed the maximum allowed rate.
     */
    public static Specification<Helper> hasMaxHourlyRate(Double maxHourlyRate) {
        if (maxHourlyRate == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get("hourlyRate"), maxHourlyRate);
    }

    /**
     * Filter helpers whose average rating meets or exceeds the minimum required rating.
     */
    public static Specification<Helper> hasMinRating(Double minRating) {
        if (minRating == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, builder) -> builder.greaterThanOrEqualTo(averageRating(root, builder), minRating);
    }

    /**
     * Filter helpers that have continuous available slots for the entire requested time window.
     * Supports both single-hour and multi-hour booking durations.
     */
    public static Specification<Helper> hasContinuousAvailableSlots(LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (date == null || startTime == null || endTime == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        long requiredHours = Math.max(1, Duration.between(startTime, endTime).toHours());
        return (root, query, builder) -> {
            Subquery<Long> availableSlotsCount = query.subquery(Long.class);
            Root<HelperAvailability> slot = availableSlotsCount.from(HelperAvailability.class);
            availableSlotsCount.select(builder.count(slot.get("id")))
                    .where(
                            builder.equal(slot.get("helper").get("id"), root.get("id")),
                            builder.equal(slot.get("slotDate"), date),
                            builder.greaterThanOrEqualTo(slot.get("startTime"), startTime),
                            builder.lessThanOrEqualTo(slot.get("endTime"), endTime),
                            builder.equal(slot.get("status"), AvailabilityStatus.AVAILABLE));
            return builder.equal(availableSlotsCount, requiredHours);
        };
    }

    /**
     * Applies standard helper ordering: lowest hourly rate first, followed by highest average rating.
     */
    public static Specification<Helper> withOrdering() {
        return (root, query, builder) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(
                        builder.asc(root.get("hourlyRate")),
                        builder.desc(averageRating(root, builder)));
            }
            return builder.conjunction();
        };
    }

    /**
     * Composable search specification matching all non-null criteria.
     */
    public static Specification<Helper> matching(HelperSearchCriteria criteria) {
        Specification<Helper> spec = Specification.where(hasLocality(criteria.getLocality()))
                .and(hasSkill(criteria.getSkill()))
                .and(hasContinuousAvailableSlots(criteria.getDate(), criteria.getStartTime(), criteria.getEndTime()));

        if (criteria.getGender() != null) {
            spec = spec.and(hasGender(criteria.getGender()));
        }
        if (criteria.getMaxHourlyRate() != null) {
            spec = spec.and(hasMaxHourlyRate(criteria.getMaxHourlyRate()));
        }
        if (criteria.getMinRating() != null) {
            spec = spec.and(hasMinRating(criteria.getMinRating()));
        }
        return spec.and(withOrdering());
    }

    public static Expression<Double> averageRating(Root<Helper> root, CriteriaBuilder builder) {
        Expression<Double> total = builder.toDouble(root.get("totalRating"));
        Expression<Double> count = builder.toDouble(root.get("ratingCount"));
        return builder.<Double>selectCase()
                .when(builder.greaterThan(root.<Long>get("ratingCount"), 0L),
                        builder.toDouble(builder.quot(total, count)))
                .otherwise(0.0);
    }
}
