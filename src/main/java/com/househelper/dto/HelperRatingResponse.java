package com.househelper.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HelperRatingResponse {

    Long helperId;
    Double rating;
    Long ratingCount;
}
