package com.househelper.repository;

import com.househelper.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomer_Id(Long customerId);

    List<Booking> findByCustomer_IdOrderByBookingDateAscStartTimeAsc(Long customerId);

    List<Booking> findByAssignedHelperId(Long assignedHelperId);

    List<Booking> findByBookingSeries_IdOrderByBookingDateAsc(Long seriesId);

    boolean existsByCustomer_Id(Long customerId);
}
