package org.IAM.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    String email,
    @NotBlank(message = "Reset code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Reset code must be 6 digits")
    String code,
    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    String newPassword
) {}
