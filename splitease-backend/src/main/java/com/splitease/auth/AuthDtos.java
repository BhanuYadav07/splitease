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

    /** The signed-in shape returned by login, verify-email and reset-password. */
    public record AuthResponse(String token, UserDtos.UserResponse user) {}

    /**
     * Sign-up stops at "account created, code on its way": there is no token here, so an
     * unconfirmed account cannot reach anything but the verification step. The payload
     * describes the code that was just emailed (masked address, expiry, resend cooldown).
     */
    public record RegisterResponse(
            UserDtos.UserResponse user,
            OtpDtos.OtpIssued verification
    ) {}
}
