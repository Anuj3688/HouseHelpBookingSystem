package com.househelper.repository;

import com.househelper.model.CustomerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerReviewRepository extends JpaRepository<CustomerReview, UUID> {

    List<CustomerReview> findByCustomer_IdOrderByCreatedAtDesc(UUID customerId);

    Optional<CustomerReview> findByBookingId(UUID bookingId);

    boolean existsByBookingId(UUID bookingId);
}
