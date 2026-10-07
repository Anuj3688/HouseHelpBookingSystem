package com.househelper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelperEmergencyCancelRequest {

    @Schema(description = "Start date for emergency leave (inclusive, defaults to today)")
    private LocalDate fromDate;

    @Schema(description = "End date for emergency leave (inclusive, optional)")
    private LocalDate toDate;

    @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
    @Schema(description = "Emergency leave reason")
    private String reason;
}
