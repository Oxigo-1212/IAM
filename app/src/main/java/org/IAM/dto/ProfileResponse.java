package org.IAM.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.IAM.repositories.user.User;

public record ProfileResponse(
    UUID userId,
    String username,
    String email,
    String fullName,
    String phone,
    String address,
    LocalDate dateOfBirth,
    String avatarUrl,
    Instant createdDate
) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
            user.getUserID(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getPhone(),
            user.getAddress(),
            user.getDateOfBirth(),
            user.getAvatarUrl(),
            user.getCreatedDate()
        );
    }
}
