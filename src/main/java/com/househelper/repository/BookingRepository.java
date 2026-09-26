package com.househelper.repository;

import com.househelper.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomer_Id(Long customerId);

    List<Booking> findByAssignedHelperId(Long assignedHelperId);

    boolean existsByCustomer_Id(Long customerId);
}
