package org.IAM.controllers;

import java.util.UUID;

import org.IAM.dto.ChangePasswordRequest;
import org.IAM.dto.ChangePasswordResponse;
import org.IAM.dto.ProfileResponse;
import org.IAM.dto.UpdateProfileRequest;
import org.IAM.services.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateProfile(
        Authentication authentication,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<ChangePasswordResponse> changePassword(
        Authentication authentication,
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        String accessToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        return ResponseEntity.ok(userService.changePassword(userId, accessToken, request));
    }
}
