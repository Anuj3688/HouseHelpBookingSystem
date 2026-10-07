package com.househelper.repository;

import com.househelper.model.Booking;
import com.househelper.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByCustomer_Id(UUID customerId);

    List<Booking> findByCustomer_IdOrderByBookingDateAscStartTimeAsc(UUID customerId);

    List<Booking> findByAssignedHelperId(UUID assignedHelperId);

    List<Booking> findByAssignedHelperIdAndStatusInAndBookingDateGreaterThanEqual(
            UUID assignedHelperId, Collection<BookingStatus> statuses, LocalDate date);

    List<Booking> findByAssignedHelperIdAndStatusInAndBookingDateBetween(
            UUID assignedHelperId, Collection<BookingStatus> statuses, LocalDate fromDate, LocalDate toDate);

    List<Booking> findByBookingSeries_IdOrderByBookingDateAsc(UUID seriesId);

    boolean existsByCustomer_Id(UUID customerId);
}
