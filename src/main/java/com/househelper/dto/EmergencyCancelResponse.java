package com.househelper.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyCancelResponse {

    private UUID helperId;
    private int totalBookingsCancelled;
    private List<UUID> cancelledBookingIds;
    private int tasksEnqueued;
    private String message;
}
