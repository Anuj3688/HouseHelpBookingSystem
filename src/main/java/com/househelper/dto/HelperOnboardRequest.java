package com.househelper.dto;

import com.househelper.model.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelperOnboardRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    @NotEmpty
    @Size(max = 3)
    private Set<@NotBlank String> localities;

    @NotEmpty
    private Set<@NotNull SkillType> skills;

    @NotNull
    @Positive
    private Double hourlyRate;

    @NotBlank
    private String governmentIdProof;
}
