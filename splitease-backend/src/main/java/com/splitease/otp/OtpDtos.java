package com.splitease.otp;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public class OtpDtos {

    /**
     * Public verification step: the address is sent in the body because there is no
     * session yet - the account exists but nobody has been logged in until the code
     * sent to that mailbox is proven. The 6-digit code is the only credential.
     */
    public record VerifyRequest(
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Verification code is required")
            @Pattern(regexp = "\\d{6}", message = "The verification code is 6 digits")
            String code
    ) {}

    /** Public resend step: same reasoning as {@link VerifyRequest}. */
    public record ResendRequest(
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email
    ) {}

    /**
     * What every "a code is on its way" answer looks like. The address is masked so a
     * response can be shown without echoing a full mailbox, and the two countdowns are
     * measured on the server (expiry and resend cooldown) so a reload - or a clock that
     * is a little off - cannot make the UI disagree with the rules being enforced.
     *
     * <p>{@code attemptsLeft} is the remaining guess budget before the mail pause.</p>
     */
    public record OtpIssued(
            String maskedEmail,
            Instant expiresAt,
            long expiresInSeconds,
            long resendAvailableInSeconds,
            int attemptsLeft
    ) {}

    /** Same payload as {@link OtpIssued}; kept separate so the reset flow reads clearly. */
    public record ResetCodeIssued(
            String maskedEmail,
            Instant expiresAt,
            long expiresInSeconds,
            long resendAvailableInSeconds,
            int attemptsLeft
    ) {}
}
