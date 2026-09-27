package com.househelper.repository;

import com.househelper.model.BookingSeries;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BookingSeriesRepository extends JpaRepository<BookingSeries, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select series from BookingSeries series where series.id = :seriesId")
    Optional<BookingSeries> findByIdForUpdate(@Param("seriesId") UUID seriesId);
}
