package com.househelper.dto;

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
public class CustomerReviewResponse {

    private UUID id;
    private UUID customerId;
    private UUID helperId;
    private UUID bookingId;
    private Integer rating;
    private String review;
    private Instant createdAt;
    private Double customerAverageRating;
    private Long customerRatingCount;
}
