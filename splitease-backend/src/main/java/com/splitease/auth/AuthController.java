package com.splitease.auth;

import com.splitease.otp.OtpDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 201 with {user, verification}: the account exists and a 5-minute code is on its
     * way, but there is no token - sign-up does not log anyone in. The session comes
     * from {@code /api/otp/verify} once the code is accepted.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthDtos.RegisterResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {

        AuthDtos.RegisterResponse created = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 200 with {token, user} for a confirmed account. An unverified one is refused with
     * 403 EMAIL_NOT_VERIFIED (and a fresh code is sent); too many wrong passwords pause
     * the account and answer 401 with the wait.
     */
    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    /**
     * 200 with the same code payload {@code /api/otp/resend} returns - for an address
     * with an account and for one without, which is the point: starting a reset must not
     * be a way to find out who is registered.
     */
    @PostMapping("/forgot-password")
    public OtpDtos.ResetCodeIssued forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    /**
     * 200 with {token, user}: the code was accepted, the password is replaced, the
     * address counts as verified, and the caller is signed in.
     */
    @PostMapping("/reset-password")
    public AuthDtos.AuthResponse resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }
}
