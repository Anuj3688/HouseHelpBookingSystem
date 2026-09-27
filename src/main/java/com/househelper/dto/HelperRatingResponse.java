package com.househelper.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HelperRatingResponse {

    UUID helperId;
    Double rating;
    Long ratingCount;
}
