package org.IAM.dto;

import java.time.LocalDate;

import org.IAM.validation.SafeWebUrl;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100, message = "Full name must be at most 100 characters")
    String fullName,
    @Pattern(regexp = "^[0-9+\\-\\s()]{7,20}$", message = "Phone number format is invalid")
    String phone,
    @Size(max = 255, message = "Address must be at most 255 characters")
    String address,
    @Past(message = "Date of birth must be in the past")
    LocalDate dateOfBirth,
    @Size(max = 500, message = "Avatar URL must be at most 500 characters")
    @SafeWebUrl
    String avatarUrl
) {}
