package com.househelper.dto;

import com.househelper.model.SkillType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelperSearchResponse {

    private Long id;
    private String name;
    private Set<String> localities;
    private Set<SkillType> skills;
    private Double hourlyRate;
    private Double rating;
}
