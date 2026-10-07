package com.househelper.dto;

import com.househelper.model.ReassignmentTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignmentTaskResponse {

    private UUID id;
    private UUID bookingId;
    private UUID originalHelperId;
    private UUID reassignedHelperId;
    private String reason;
    private ReassignmentTaskStatus status;
    private Integer attempts;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
}
