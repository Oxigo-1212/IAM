package org.IAM.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private static final String TEST_SECRET = "test-secret-key-at-least-32-bytes-long-1234";

    private UserRepository userRepository;
    private ProfileCacheService profileCacheService;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private TokenStoreService tokenStoreService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileCacheService = mock(ProfileCacheService.class);
        passwordEncoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
        jwtService = new JwtService(TEST_SECRET, 900);
        tokenStoreService = mock(TokenStoreService.class);
        userService = new UserService(
            userRepository,
            profileCacheService,
            passwordEncoder,
            jwtService,
            tokenStoreService
        );
    }

    @Test
    void getProfileReturnsCachedValueWithoutQueryingDatabase() {
        UUID userId = UUID.randomUUID();
        ProfileResponse cached = new ProfileResponse(
            userId, "alice", "alice@example.com", "Alice Nguyen", null, null, null, null, Instant.now()
        );
        when(profileCacheService.get(userId)).thenReturn(Optional.of(cached));

        ProfileResponse result = userService.getProfile(userId);

        assertEquals("Alice Nguyen", result.fullName());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getProfileLoadsFromDatabaseAndCachesOnMiss() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setUserID(userId);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setFullName("Alice Nguyen");

        when(profileCacheService.get(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ProfileResponse result = userService.getProfile(userId);

        assertEquals("alice@example.com", result.email());
        verify(profileCacheService).put(eq(userId), eq(result));
    }

    @Test
    void updateProfileSavesChangesAndEvictsCache() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setUserID(userId);
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(
            "  Alice Tran  ",
            "+84 912345678",
            "Ho Chi Minh City",
            LocalDate.of(2000, 5, 10),
            "https://cdn.example.com/avatar.png"
        );

        ProfileResponse updated = userService.updateProfile(userId, request);

        assertEquals("Alice Tran", updated.fullName());
        assertEquals("+84 912345678", updated.phone());
        assertEquals("alice@example.com", updated.email());
        verify(profileCacheService).evict(userId);
    }

    @Test
    void getProfileThrowsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(profileCacheService.get(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getProfile(userId));
    }

    @Test
    void changePasswordUpdatesHashAndBlocklistsCurrentAccessToken() {
        UUID userId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(accessToken).tokenId();

        User user = new User();
        user.setUserID(userId);
        user.setPassword(passwordEncoder.encode("oldPassword123"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChangePasswordResponse response = userService.changePassword(
            userId,
            accessToken,
            new ChangePasswordRequest("oldPassword123", "newPassword456")
        );

        assertTrue(response.success());
        assertTrue(passwordEncoder.matches("newPassword456", user.getPassword()));
        verify(userRepository).save(user);
        verify(tokenStoreService).blocklistAccessToken(eq(tokenId), any(Duration.class));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        UUID userId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(userId);

        User user = new User();
        user.setUserID(userId);
        user.setPassword(passwordEncoder.encode("oldPassword123"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(
            InvalidCredentialsException.class,
            () -> userService.changePassword(
                userId,
                accessToken,
                new ChangePasswordRequest("wrongPassword", "newPassword456")
            )
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordCancelsWhenNewPasswordMatchesCurrentPassword() {
        UUID userId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(userId);

        User user = new User();
        user.setUserID(userId);
        user.setPassword(passwordEncoder.encode("samePassword123"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(
            InvalidPasswordChangeException.class,
            () -> userService.changePassword(
                userId,
                accessToken,
                new ChangePasswordRequest("samePassword123", "samePassword123")
            )
        );

        verify(userRepository, never()).save(any());
        verify(tokenStoreService, never()).blocklistAccessToken(any(), any());
    }
}
