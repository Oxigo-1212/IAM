package org.IAM.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.IAM.config.SecurityConfig;
import org.IAM.dto.AuthTokenResponse;
import org.IAM.dto.ForgotPasswordRequest;
import org.IAM.dto.LoginRequest;
import org.IAM.dto.LogoutResponse;
import org.IAM.dto.OperationResponse;
import org.IAM.dto.ProfileResponse;
import org.IAM.dto.RefreshRequest;
import org.IAM.dto.RegisterRequest;
import org.IAM.dto.RegisterResponse;
import org.IAM.dto.ResetPasswordRequest;
import org.IAM.exceptions.DuplicateEmailException;
import org.IAM.exceptions.GlobalExceptionHandler;
import org.IAM.exceptions.InvalidCredentialsException;
import org.IAM.exceptions.InvalidResetCodeException;
import org.IAM.services.AuthService;
import org.IAM.services.JwtService;
import org.IAM.services.TokenStoreService;
import org.IAM.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TokenStoreService tokenStoreService;

    @Test
    void registerReturns201AndSuccessTrueWithoutAuth() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(new RegisterResponse(true));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","email":"alice@example.com","password":"password123"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void registerReturns400ForInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"","email":"not-an-email","password":"short"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void registerReturns409ForDuplicateEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
            .thenThrow(new DuplicateEmailException("Email is already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","email":"alice@example.com","password":"password123"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Email is already registered"));
    }

    @Test
    void registerReturns409OnConcurrentUniqueConstraintViolation() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
            .thenThrow(new DataIntegrityViolationException("unique_email"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","email":"alice@example.com","password":"password123"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Email is already registered"));
    }

    @Test
    void loginReturns200AndTokensWithoutAuth() throws Exception {
        when(authService.login(any(LoginRequest.class)))
            .thenReturn(new AuthTokenResponse("access.jwt.token", "rt-123", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice@example.com","password":"password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access.jwt.token"))
            .andExpect(jsonPath("$.refreshToken").value("rt-123"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginReturns401OnInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice@example.com","password":"wrong"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void refreshReturns200WithNewAccessToken() throws Exception {
        when(authService.refresh(any(RefreshRequest.class)))
            .thenReturn(new AuthTokenResponse("new.jwt.token", "rt-123", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"rt-123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new.jwt.token"));
    }

    @Test
    void protectedEndpointRejectsMissingTokenWith401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAllowsValidNonBlocklistedJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(token).tokenId();

        ProfileResponse profile = new ProfileResponse(
            userId, "alice", "alice@example.com", "Alice Nguyen", null, null, null, null, null
        );

        when(tokenStoreService.isAccessTokenBlocklisted(tokenId)).thenReturn(false);
        when(userService.getProfile(userId)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void protectedEndpointRejectsBlocklistedJwtWith401() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(token).tokenId();

        when(tokenStoreService.isAccessTokenBlocklisted(tokenId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or expired token"));
    }

    @Test
    void logoutReturns200AndSuccessTrueWhenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(token).tokenId();

        when(tokenStoreService.isAccessTokenBlocklisted(tokenId)).thenReturn(false);
        when(authService.logout(token, "rt-123")).thenReturn(new LogoutResponse(true));

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"rt-123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void logoutRejectsUnauthenticatedRequestWith401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"rt-123"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordReturns200WithoutAuth() throws Exception {
        when(authService.requestPasswordReset(any(ForgotPasswordRequest.class)))
            .thenReturn(new OperationResponse(true));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice@gmail.com"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void resetPasswordReturns200OnValidCode() throws Exception {
        when(authService.resetPassword(any(ResetPasswordRequest.class)))
            .thenReturn(new OperationResponse(true));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice@gmail.com","code":"123456","newPassword":"brandNewPass123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void resetPasswordReturns400OnInvalidCode() throws Exception {
        when(authService.resetPassword(any(ResetPasswordRequest.class)))
            .thenThrow(new InvalidResetCodeException("Invalid or expired reset code"));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice@gmail.com","code":"000000","newPassword":"brandNewPass123"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid or expired reset code"));
    }
}
