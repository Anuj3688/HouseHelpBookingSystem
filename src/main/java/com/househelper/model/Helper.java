package com.househelper.model;

import com.househelper.converter.EncryptedStringConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "helpers", uniqueConstraints = @UniqueConstraint(name = "uk_helper_phone", columnNames = "phone"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Helper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false)
    private String phone;

    @ElementCollection
    @CollectionTable(name = "helper_localities", joinColumns = @JoinColumn(name = "helper_id"))
    @Column(name = "locality", nullable = false)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<String> localities = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "helper_skills", joinColumns = @JoinColumn(name = "helper_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<SkillType> skills = new HashSet<>();

    @NotNull
    @Positive
    @Column(nullable = false)
    private Double hourlyRate;

    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Double rating = 0.0;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "government_id_proof", length = 2048)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String governmentIdProof;
}
