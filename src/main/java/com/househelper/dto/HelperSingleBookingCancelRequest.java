package com.househelper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelperSingleBookingCancelRequest {

    @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
    @Schema(description = "Cancellation reason")
    private String reason;
}
