package org.IAM.services;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.IAM.dto.ChangePasswordRequest;
import org.IAM.dto.ChangePasswordResponse;
import org.IAM.dto.ProfileResponse;
import org.IAM.dto.UpdateProfileRequest;
import org.IAM.exceptions.InvalidCredentialsException;
import org.IAM.exceptions.InvalidPasswordChangeException;
import org.IAM.exceptions.UserNotFoundException;
import org.IAM.repositories.user.User;
import org.IAM.repositories.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProfileCacheService profileCacheService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStoreService tokenStoreService;

    public UserService(
        UserRepository userRepository,
        ProfileCacheService profileCacheService,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        TokenStoreService tokenStoreService
    ) {
        this.userRepository = userRepository;
        this.profileCacheService = profileCacheService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStoreService = tokenStoreService;
    }

    public ProfileResponse getProfile(UUID userId) {
        return profileCacheService.get(userId)
            .orElseGet(() -> loadAndCacheProfile(userId));
    }

    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setFullName(trimOrNull(request.fullName()));
        user.setPhone(trimOrNull(request.phone()));
        user.setAddress(trimOrNull(request.address()));
        user.setDateOfBirth(request.dateOfBirth());
        user.setAvatarUrl(trimOrNull(request.avatarUrl()));

        User saved = userRepository.save(user);
        profileCacheService.evict(userId);
        return ProfileResponse.from(saved);
    }

    public ChangePasswordResponse changePassword(
        UUID userId,
        String accessToken,
        ChangePasswordRequest request
    ) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (request.newPassword().equals(request.currentPassword())
            || passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidPasswordChangeException("New password must differ from current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        JwtService.JwtPayload payload = jwtService.parseAndValidate(accessToken);
        Duration remainingTtl = Duration.between(Instant.now(), payload.expiresAt());
        tokenStoreService.blocklistAccessToken(payload.tokenId(), remainingTtl);

        return new ChangePasswordResponse(true);
    }

    private ProfileResponse loadAndCacheProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));
        ProfileResponse profile = ProfileResponse.from(user);
        profileCacheService.put(userId, profile);
        return profile;
    }

    private String trimOrNull(String value) {
        return value != null ? value.trim() : null;
    }
}
