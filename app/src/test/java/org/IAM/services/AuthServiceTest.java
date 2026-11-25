package org.IAM.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.IAM.dto.AuthTokenResponse;
import org.IAM.dto.ForgotPasswordRequest;
import org.IAM.dto.LoginRequest;
import org.IAM.dto.LogoutResponse;
import org.IAM.dto.OperationResponse;
import org.IAM.dto.RefreshRequest;
import org.IAM.dto.RegisterRequest;
import org.IAM.dto.RegisterResponse;
import org.IAM.dto.ResetPasswordRequest;
import org.IAM.exceptions.DuplicateEmailException;
import org.IAM.exceptions.InvalidCredentialsException;
import org.IAM.exceptions.InvalidResetCodeException;
import org.IAM.repositories.user.User;
import org.IAM.repositories.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String TEST_SECRET = "test-secret-key-at-least-32-bytes-long-1234";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private TokenStoreService tokenStoreService;
    private PasswordResetStoreService passwordResetStoreService;
    private EmailService emailService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
        jwtService = new JwtService(TEST_SECRET, 900);
        tokenStoreService = mock(TokenStoreService.class);
        passwordResetStoreService = mock(PasswordResetStoreService.class);
        emailService = mock(EmailService.class);
        authService = new AuthService(
            userRepository,
            passwordEncoder,
            jwtService,
            tokenStoreService,
            passwordResetStoreService,
            emailService
        );
    }

    @Test
    void registerHashesPasswordWithBcryptAndSavesNormalizedEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        RegisterResponse response = authService.register(
            new RegisterRequest("  alice  ", "  Alice@Example.com ", "secret123")
        );

        assertTrue(response.success());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertEquals("alice", saved.getUsername());
        assertEquals("alice@example.com", saved.getEmail());
        assertTrue(saved.getPassword().startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches("secret123", saved.getPassword()));
    }

    @Test
    void registerRejectsDuplicateEmailBeforeSaving() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(
            DuplicateEmailException.class,
            () -> authService.register(new RegisterRequest("alice", "alice@example.com", "secret123"))
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginIssuesAccessJwtAndStoresRefreshToken() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setUserID(userId);
        user.setEmail("alice@example.com");
        user.setPassword(passwordEncoder.encode("secret123"));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        AuthTokenResponse response = authService.login(new LoginRequest("  Alice@Example.com ", "secret123"));

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresIn());

        JwtService.JwtPayload parsed = jwtService.parseAndValidate(response.accessToken());
        assertEquals(userId, parsed.userId());
        assertNotNull(parsed.tokenId());

        verify(tokenStoreService).saveRefreshToken(eq(response.refreshToken()), eq(userId));
    }

    @Test
    void loginReturnsGenericErrorForMissingEmailAndWrongPassword() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        InvalidCredentialsException missingEx = assertThrows(
            InvalidCredentialsException.class,
            () -> authService.login(new LoginRequest("missing@example.com", "secret123"))
        );

        User user = new User();
        user.setUserID(UUID.randomUUID());
        user.setEmail("alice@example.com");
        user.setPassword(passwordEncoder.encode("secret123"));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        InvalidCredentialsException wrongPassEx = assertThrows(
            InvalidCredentialsException.class,
            () -> authService.login(new LoginRequest("alice@example.com", "wrong-password"))
        );

        assertEquals("Invalid email or password", missingEx.getMessage());
        assertEquals(missingEx.getMessage(), wrongPassEx.getMessage());
    }

    @Test
    void refreshIssuesNewAccessTokenWhenRefreshTokenExistsInRedis() {
        UUID userId = UUID.randomUUID();
        when(tokenStoreService.findUserIdByRefreshToken("rt-123")).thenReturn(Optional.of(userId));

        AuthTokenResponse response = authService.refresh(new RefreshRequest("rt-123"));

        assertEquals("rt-123", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresIn());
        assertEquals(userId, jwtService.parseAndValidate(response.accessToken()).userId());
    }

    @Test
    void refreshRejectsUnknownOrExpiredRefreshToken() {
        when(tokenStoreService.findUserIdByRefreshToken("expired-rt")).thenReturn(Optional.empty());

        assertThrows(
            InvalidCredentialsException.class,
            () -> authService.refresh(new RefreshRequest("expired-rt"))
        );
    }

    @Test
    void logoutBlocklistsAccessTokenAndDeletesCallerOwnedRefreshToken() {
        UUID aliceId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(aliceId);
        String tokenId = jwtService.parseAndValidate(accessToken).tokenId();

        when(tokenStoreService.findUserIdByRefreshToken("alice-rt")).thenReturn(Optional.of(aliceId));

        LogoutResponse response = authService.logout(accessToken, "alice-rt");

        assertTrue(response.success());
        verify(tokenStoreService).blocklistAccessToken(eq(tokenId), any(Duration.class));
        verify(tokenStoreService).deleteRefreshToken("alice-rt");
    }

    @Test
    void logoutDoesNotDeleteAnotherUsersRefreshToken() {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        String aliceToken = jwtService.generateAccessToken(aliceId);
        String aliceTokenId = jwtService.parseAndValidate(aliceToken).tokenId();

        when(tokenStoreService.findUserIdByRefreshToken("bob-rt")).thenReturn(Optional.of(bobId));

        LogoutResponse response = authService.logout(aliceToken, "bob-rt");

        assertTrue(response.success());
        verify(tokenStoreService).blocklistAccessToken(eq(aliceTokenId), any(Duration.class));
        verify(tokenStoreService, never()).deleteRefreshToken("bob-rt");
    }

    @Test
    void requestPasswordResetSavesSixDigitCodeAndEmailsRegisteredUser() {
        User user = new User();
        user.setEmail("alice@gmail.com");
        when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));

        OperationResponse response = authService.requestPasswordReset(
            new ForgotPasswordRequest("  Alice@Gmail.com ")
        );

        assertTrue(response.success());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetStoreService).saveCode(eq("alice@gmail.com"), codeCaptor.capture());
        String code = codeCaptor.getValue();
        assertTrue(code.matches("^[0-9]{6}$"));
        verify(emailService).sendPasswordResetCode("alice@gmail.com", code);
    }

    @Test
    void requestPasswordResetReturnsSuccessWithoutEmailingUnknownAddress() {
        when(userRepository.findByEmail("ghost@gmail.com")).thenReturn(Optional.empty());

        OperationResponse response = authService.requestPasswordReset(
            new ForgotPasswordRequest("ghost@gmail.com")
        );

        assertTrue(response.success());
        verify(passwordResetStoreService, never()).saveCode(any(), any());
        verify(emailService, never()).sendPasswordResetCode(any(), any());
    }

    @Test
    void resetPasswordUpdatesBcryptHashWhenCodeIsValid() {
        User user = new User();
        user.setEmail("alice@gmail.com");
        user.setPassword(passwordEncoder.encode("oldPassword123"));

        when(passwordResetStoreService.verifyAndConsume("alice@gmail.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));

        OperationResponse response = authService.resetPassword(
            new ResetPasswordRequest("alice@gmail.com", "123456", "brandNewPass123")
        );

        assertTrue(response.success());
        assertTrue(passwordEncoder.matches("brandNewPass123", user.getPassword()));
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordRejectsInvalidOrExpiredCodeWithoutSaving() {
        when(passwordResetStoreService.verifyAndConsume("alice@gmail.com", "000000")).thenReturn(false);

        assertThrows(
            InvalidResetCodeException.class,
            () -> authService.resetPassword(
                new ResetPasswordRequest("alice@gmail.com", "000000", "brandNewPass123")
            )
        );

        verify(userRepository, never()).save(any());
    }
}
