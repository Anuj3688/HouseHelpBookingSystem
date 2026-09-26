package com.househelper.repository;

import com.househelper.model.SystemEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SystemEventRepository extends JpaRepository<SystemEvent, Long> {

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
            @Param("helperId") Long helperId,
            @Param("customerId") Long customerId,
            @Param("paymentId") Long paymentId,
            @Param("bookingId") Long bookingId,
            @Param("seriesId") Long seriesId);
}
