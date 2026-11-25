package org.IAM.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.IAM.config.SecurityConfig;
import org.IAM.dto.ChangePasswordRequest;
import org.IAM.dto.ChangePasswordResponse;
import org.IAM.dto.ProfileResponse;
import org.IAM.dto.UpdateProfileRequest;
import org.IAM.exceptions.GlobalExceptionHandler;
import org.IAM.exceptions.InvalidCredentialsException;
import org.IAM.exceptions.InvalidPasswordChangeException;
import org.IAM.services.JwtService;
import org.IAM.services.TokenStoreService;
import org.IAM.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TokenStoreService tokenStoreService;

    @Test
    void updateProfileReturns200WithUpdatedFields() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(token).tokenId();

        when(tokenStoreService.isAccessTokenBlocklisted(tokenId)).thenReturn(false);
        when(userService.updateProfile(eq(userId), any(UpdateProfileRequest.class)))
            .thenReturn(new ProfileResponse(
                userId, "alice", "alice@example.com", "Alice Tran", "+84912345678",
                "HCMC", LocalDate.of(2000, 5, 10), null, null
            ));

        mockMvc.perform(put("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Alice Tran","phone":"+84912345678","address":"HCMC","dateOfBirth":"2000-05-10"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Alice Tran"))
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void updateProfileReturns400OnInvalidPhoneOrFutureDateOfBirth() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(token).tokenId();

        when(tokenStoreService.isAccessTokenBlocklisted(tokenId)).thenReturn(false);

        mockMvc.perform(put("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"phone":"abc","dateOfBirth":"2099-01-01"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void changePasswordReturns200OnSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(token).tokenId();

        when(tokenStoreService.isAccessTokenBlocklisted(tokenId)).thenReturn(false);
        when(userService.changePassword(eq(userId), eq(token), any(ChangePasswordRequest.class)))
            .thenReturn(new ChangePasswordResponse(true));

        mockMvc.perform(put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"oldPassword123","newPassword":"newPassword456"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void changePasswordReturns400WhenNewMatchesCurrent() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(token).tokenId();

        when(tokenStoreService.isAccessTokenBlocklisted(tokenId)).thenReturn(false);
        when(userService.changePassword(eq(userId), eq(token), any(ChangePasswordRequest.class)))
            .thenThrow(new InvalidPasswordChangeException("New password must differ from current password"));

        mockMvc.perform(put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"samePassword123","newPassword":"samePassword123"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("New password must differ from current password"));
    }

    @Test
    void changePasswordReturns401WhenCurrentPasswordWrong() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId);
        String tokenId = jwtService.parseAndValidate(token).tokenId();

        when(tokenStoreService.isAccessTokenBlocklisted(tokenId)).thenReturn(false);
        when(userService.changePassword(eq(userId), eq(token), any(ChangePasswordRequest.class)))
            .thenThrow(new InvalidCredentialsException("Current password is incorrect"));

        mockMvc.perform(put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"wrongPassword","newPassword":"newPassword456"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Current password is incorrect"));
    }
}
