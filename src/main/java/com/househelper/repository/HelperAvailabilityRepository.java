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

public interface HelperAvailabilityRepository extends JpaRepository<HelperAvailability, Long> {

    Optional<HelperAvailability> findByHelperIdAndSlotDateAndStartTime(
            Long helperId, LocalDate slotDate, LocalTime startTime);

    @Query("""
            select distinct availability
            from HelperAvailability availability
            join fetch availability.helper helper
            where availability.status = :status
            order by availability.slotDate asc, availability.startTime asc,
                     helper.hourlyRate asc, helper.rating desc
            """)
    List<HelperAvailability> findAllSlotsByStatusWithHelper(@Param("status") AvailabilityStatus status);

    @Query("""
            select case when count(availability) > 0 then true else false end
            from HelperAvailability availability
            where availability.helper.id = :helperId
              and availability.slotDate = :slotDate
              and availability.startTime <> :sameStartTime
              and availability.startTime < :endTime
              and availability.endTime > :startTime
            """)
    boolean existsOverlappingAvailability(@Param("helperId") Long helperId,
                                          @Param("slotDate") LocalDate slotDate,
                                          @Param("sameStartTime") LocalTime sameStartTime,
                                          @Param("startTime") LocalTime startTime,
                                          @Param("endTime") LocalTime endTime);

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
            order by helper.hourlyRate asc, helper.rating desc
            """)
    List<HelperAvailability> findAvailableHelpersForSlot(
            @Param("locality") String locality,
            @Param("skill") SkillType skill,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("status") AvailabilityStatus status);
}
