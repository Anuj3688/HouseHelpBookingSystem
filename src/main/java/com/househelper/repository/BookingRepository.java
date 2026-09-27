package com.househelper.repository;

import com.househelper.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByCustomer_Id(UUID customerId);

    List<Booking> findByCustomer_IdOrderByBookingDateAscStartTimeAsc(UUID customerId);

    List<Booking> findByAssignedHelperId(UUID assignedHelperId);

    List<Booking> findByBookingSeries_IdOrderByBookingDateAsc(UUID seriesId);

    boolean existsByCustomer_Id(UUID customerId);
}
