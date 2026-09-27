package com.househelper.repository;

import com.househelper.model.SystemEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SystemEventRepository extends JpaRepository<SystemEvent, UUID> {

    @Query("""
            select event from SystemEvent event
            where (:helperId is null or event.helperId = :helperId)
              and (:customerId is null or event.customerId = :customerId)
              and (:paymentId is null or event.paymentId = :paymentId)
              and (:bookingId is null or event.bookingId = :bookingId)
              and (:seriesId is null or event.seriesId = :seriesId)
            order by event.createdAt desc
            """)
    List<SystemEvent> findAllFiltered(
            @Param("helperId") UUID helperId,
            @Param("customerId") UUID customerId,
            @Param("paymentId") UUID paymentId,
            @Param("bookingId") UUID bookingId,
            @Param("seriesId") UUID seriesId);
}
