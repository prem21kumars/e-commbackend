package com.ecommerce.modules.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class UpdateProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,20}$", message = "Phone number must be a valid phone format")
    private String phoneNumber;

    private LocalDate dateOfBirth;

    private String gender;

    private String avatarUrl;

    private Boolean newsletterSubscribed;
}

