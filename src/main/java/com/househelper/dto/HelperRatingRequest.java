package com.househelper.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class HelperRatingRequest {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;
}
