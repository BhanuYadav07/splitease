package com.splitease.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public class OtpDtos {

    public record VerifyRequest(
            @NotBlank(message = "Verification code is required")
            @Pattern(regexp = "\\d{6}", message = "The verification code is 6 digits")
            String code
    ) {}

    public record OtpIssued(
            String email,
            Instant expiresAt,
            long expiresInSeconds,
            int attemptsLeft
    ) {}

    public record ResetCodeIssued(
            String email,
            Instant expiresAt,
            long expiresInSeconds
    ) {}
}
