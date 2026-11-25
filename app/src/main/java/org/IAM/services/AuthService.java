package org.IAM.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.IAM.dto.AuthTokenResponse;
import org.IAM.dto.LoginRequest;
import org.IAM.dto.LogoutResponse;
import org.IAM.dto.RefreshRequest;
import org.IAM.dto.RegisterRequest;
import org.IAM.dto.RegisterResponse;
import org.IAM.exceptions.DuplicateEmailException;
import org.IAM.exceptions.InvalidCredentialsException;
import org.IAM.repositories.user.User;
import org.IAM.repositories.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStoreService tokenStoreService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        TokenStoreService tokenStoreService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStoreService = tokenStoreService;
    }

    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        // Fast-path check avoids BCrypt CPU cost on obvious duplicates;
        // PostgreSQL's UNIQUE index enforces uniqueness under concurrent inserts.
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));

        userRepository.save(user);
        return new RegisterResponse(true);
    }

    public AuthTokenResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        // Return the same generic 401 error for missing email and wrong password
        // to limit account enumeration.
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        String accessToken = jwtService.generateAccessToken(user.getUserID());
        String refreshToken = UUID.randomUUID().toString();
        tokenStoreService.saveRefreshToken(refreshToken, user.getUserID());

        return new AuthTokenResponse(
            accessToken,
            refreshToken,
            BEARER_TOKEN_TYPE,
            jwtService.getAccessExpirationSeconds()
        );
    }

    public AuthTokenResponse refresh(RefreshRequest request) {
        UUID userId = tokenStoreService.findUserIdByRefreshToken(request.refreshToken())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

        String newAccessToken = jwtService.generateAccessToken(userId);
        return new AuthTokenResponse(
            newAccessToken,
            request.refreshToken(),
            BEARER_TOKEN_TYPE,
            jwtService.getAccessExpirationSeconds()
        );
    }

    public LogoutResponse logout(String accessToken, String refreshToken) {
        JwtService.JwtPayload payload = jwtService.parseAndValidate(accessToken);
        Duration remainingTtl = Duration.between(Instant.now(), payload.expiresAt());
        tokenStoreService.blocklistAccessToken(payload.tokenId(), remainingTtl);

        if (refreshToken != null && !refreshToken.isBlank()) {
            // Confirm refresh token ownership before deleting so one user cannot
            // force-logout another user's session.
            tokenStoreService.findUserIdByRefreshToken(refreshToken)
                .filter(ownerUserId -> ownerUserId.equals(payload.userId()))
                .ifPresent(ownerUserId -> tokenStoreService.deleteRefreshToken(refreshToken));
        }

        return new LogoutResponse(true);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
