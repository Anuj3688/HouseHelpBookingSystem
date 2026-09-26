package com.househelper.repository;

import com.househelper.dto.HelperSearchCriteria;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.Helper;
import com.househelper.model.HelperAvailability;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HelperSpecifications {

    private HelperSpecifications() {
    }

    public static Specification<Helper> matching(HelperSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Subquery<Integer> localityMatch = query.subquery(Integer.class);
            Root<Helper> localityHelper = localityMatch.from(Helper.class);
            Join<Helper, String> locality = localityHelper.join("localities");
            localityMatch.select(builder.literal(1))
                    .where(
                            builder.equal(localityHelper.get("id"), root.get("id")),
                            builder.equal(builder.lower(locality),
                                    criteria.getLocality().trim().toLowerCase(Locale.ROOT)));
            predicates.add(builder.exists(localityMatch));

            Subquery<Integer> skillMatch = query.subquery(Integer.class);
            Root<Helper> skillHelper = skillMatch.from(Helper.class);
            Join<Helper, ?> skill = skillHelper.join("skills");
            skillMatch.select(builder.literal(1))
                    .where(
                            builder.equal(skillHelper.get("id"), root.get("id")),
                            builder.equal(skill, criteria.getSkill()));
            predicates.add(builder.exists(skillMatch));

            if (criteria.getGender() != null) {
                predicates.add(builder.equal(root.get("gender"), criteria.getGender()));
            }
            if (criteria.getMaxHourlyRate() != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("hourlyRate"), criteria.getMaxHourlyRate()));
            }
            if (criteria.getMinRating() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        averageRating(root, builder), criteria.getMinRating()));
            }

            Subquery<Integer> availableSlot = query.subquery(Integer.class);
            Root<HelperAvailability> slot = availableSlot.from(HelperAvailability.class);
            availableSlot.select(builder.literal(1))
                    .where(
                            builder.equal(slot.get("helper").get("id"), root.get("id")),
                            builder.equal(slot.get("slotDate"), criteria.getDate()),
                            builder.equal(slot.get("startTime"), criteria.getStartTime()),
                            builder.equal(slot.get("endTime"), criteria.getEndTime()),
                            builder.equal(slot.get("status"), AvailabilityStatus.AVAILABLE));
            predicates.add(builder.exists(availableSlot));

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(
                        builder.asc(root.get("hourlyRate")),
                        builder.desc(averageRating(root, builder)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Expression<Double> averageRating(Root<Helper> root, CriteriaBuilder builder) {
        Expression<Double> total = builder.toDouble(root.get("totalRating"));
        Expression<Double> count = builder.toDouble(root.get("ratingCount"));
        return builder.<Double>selectCase()
                .when(builder.greaterThan(root.<Long>get("ratingCount"), 0L),
                        builder.toDouble(builder.quot(total, count)))
                .otherwise(0.0);
    }
}
