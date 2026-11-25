package org.IAM.controllers;

import org.IAM.dto.AuthTokenResponse;
import org.IAM.dto.LoginRequest;
import org.IAM.dto.LogoutRequest;
import org.IAM.dto.LogoutResponse;
import org.IAM.dto.RefreshRequest;
import org.IAM.dto.RegisterRequest;
import org.IAM.dto.RegisterResponse;
import org.IAM.services.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
        @RequestBody(required = false) LogoutRequest request
    ) {
        String accessToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        String refreshToken = request != null ? request.refreshToken() : null;
        return ResponseEntity.ok(authService.logout(accessToken, refreshToken));
    }
}
