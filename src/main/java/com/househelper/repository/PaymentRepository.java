package com.househelper.repository;

import com.househelper.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByBookingId(UUID bookingId);

    List<Payment> findByBookingIdIn(List<UUID> bookingIds);

    List<Payment> findByBookingSeriesId(UUID bookingSeriesId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);
}
