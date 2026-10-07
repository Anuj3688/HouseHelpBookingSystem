package com.househelper.repository;

import com.househelper.model.ReassignmentTask;
import com.househelper.model.ReassignmentTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReassignmentTaskRepository extends JpaRepository<ReassignmentTask, UUID> {

    List<ReassignmentTask> findByStatusOrderByCreatedAtAsc(ReassignmentTaskStatus status);

    Optional<ReassignmentTask> findByBookingId(UUID bookingId);

    List<ReassignmentTask> findByOriginalHelperId(UUID originalHelperId);
}
