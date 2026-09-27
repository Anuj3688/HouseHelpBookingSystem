package com.househelper.dto;

import com.househelper.model.BookingStatus;
import com.househelper.model.SkillType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private UUID id;
    private UUID seriesId;
    private UUID paymentId;
    private UUID customerId;
    private UUID assignedHelperId;
    private String locality;
    private SkillType skill;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Double totalAmount;
    private BookingStatus status;
}
