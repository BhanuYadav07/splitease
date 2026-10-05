package com.splitease.auth;

import com.splitease.otp.OtpDtos;
import com.splitease.user.UserDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank(message = "Name is required") String name,
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters") String password
    ) {}

    public record LoginRequest(
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password
    ) {}

    /**
     * Step 1 of a password reset. No password, no code - just the address whose mailbox
     * has to prove itself. Validation is identical to the other email fields so a typo
     * is caught here rather than after a wasted round trip.
     */
    public record ForgotPasswordRequest(
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email
    ) {}

    /**
     * Step 2: the emailed code stands in for the old password. {@code newPassword} is
     * named that way on purpose - {@code password} would be ambiguous in a request that
     * already carries a {@code code}.
     */
    public record ResetPasswordRequest(
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Verification code is required")
            @Pattern(regexp = "\\d{6}", message = "The verification code is 6 digits") String code,
            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters") String newPassword
    ) {}

    public record AuthResponse(String token, UserDtos.UserResponse user) {}

    /**
     * Sign-up no longer stops at "account created": the same token shape as login
     * is returned so the client can go straight to the dashboard, plus the payload
     * describing the verification code that was just emailed.
     */
    public record RegisterResponse(
            String token,
            UserDtos.UserResponse user,
            OtpDtos.OtpIssued verification
    ) {}
}
