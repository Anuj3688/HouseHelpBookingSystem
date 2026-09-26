package com.househelper.dto;

import com.househelper.model.BookingStatus;
import com.househelper.model.SkillType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private Long customerId;
    private Long assignedHelperId;
    private String locality;
    private SkillType skill;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Double totalAmount;
    private BookingStatus status;
}
