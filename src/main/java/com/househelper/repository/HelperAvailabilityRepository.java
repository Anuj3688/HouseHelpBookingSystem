package com.househelper.repository;

import com.househelper.model.AvailabilityStatus;
import com.househelper.model.HelperAvailability;
import com.househelper.model.SkillType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelperAvailabilityRepository extends JpaRepository<HelperAvailability, UUID> {

    Optional<HelperAvailability> findByHelperIdAndSlotDateAndStartTime(
            UUID helperId, LocalDate slotDate, LocalTime startTime);

    List<HelperAvailability> findByHelperIdAndSlotDateBetween(
            UUID helperId, LocalDate fromDate, LocalDate toDate);

    List<HelperAvailability> findByHelperIdAndSlotDateGreaterThanEqual(
            UUID helperId, LocalDate fromDate);

    @Query("""
            select availability from HelperAvailability availability
            where availability.helper.id = :helperId
              and availability.slotDate = :slotDate
              and availability.startTime >= :startTime
              and availability.endTime <= :endTime
              and availability.status = :status
            order by availability.startTime asc
            """)
    List<HelperAvailability> findSlotsInWindow(
            @Param("helperId") UUID helperId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("status") AvailabilityStatus status);

    @Query("""
            select availability
            from HelperAvailability availability
            join fetch availability.helper helper
            where availability.status = :status
            order by availability.slotDate asc, availability.startTime asc,
                     helper.hourlyRate asc,
                     case when helper.ratingCount > 0
                          then helper.totalRating / helper.ratingCount else 0 end desc
            """)
    List<HelperAvailability> findAllSlotsByStatusWithHelper(@Param("status") AvailabilityStatus status);

    @Query("""
            select distinct availability
            from HelperAvailability availability
            join fetch availability.helper helper
            join helper.localities locality
            join helper.skills skill
            where lower(locality) = lower(:locality)
              and skill = :skill
              and availability.slotDate = :slotDate
              and availability.startTime = :startTime
              and availability.endTime = :endTime
              and availability.status = :status
            order by helper.hourlyRate asc,
                     case when helper.ratingCount > 0
                          then helper.totalRating / helper.ratingCount else 0 end desc
            """)
    List<HelperAvailability> findAvailableHelpersForSlot(
            @Param("locality") String locality,
            @Param("skill") SkillType skill,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("status") AvailabilityStatus status);

    @Query("""
            select distinct availability
            from HelperAvailability availability
            join fetch availability.helper helper
            join helper.localities locality
            join helper.skills skill
            where lower(locality) = lower(:locality)
              and skill = :skill
              and availability.slotDate = :slotDate
              and availability.startTime >= :earliestStartTime
              and availability.status = :status
            order by availability.startTime asc, helper.hourlyRate asc,
                     case when helper.ratingCount > 0
                          then helper.totalRating / helper.ratingCount else 0 end desc
            """)
    List<HelperAvailability> findAvailableSlotsFrom(
            @Param("locality") String locality,
            @Param("skill") SkillType skill,
            @Param("slotDate") LocalDate slotDate,
            @Param("earliestStartTime") LocalTime earliestStartTime,
            @Param("status") AvailabilityStatus status);
}
