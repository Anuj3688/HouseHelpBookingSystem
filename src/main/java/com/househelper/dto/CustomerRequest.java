package com.househelper.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 1000)
    private String address;

    @Size(max = 20)
    private String phone;

    @Email
    @Size(max = 255)
    private String email;

    public CustomerRequest(String name, String address) {
        this.name = name;
        this.address = address;
    }
}
