package com.splitease.otp;

import com.splitease.auth.AuthDtos;
import com.splitease.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, session-free verification. The 6-digit code mailed to an address is the only
 * credential these two endpoints accept, which is what makes "confirm your sign-up" and
 * "you changed your email" work without first logging the (unverified) account in.
 */
@RestController
@RequestMapping("/api/otp")
@RequiredArgsConstructor
public class OtpController {

    private final AuthService authService;

    /** 200 with {token, user}: the mailbox is proven and the account is signed in. */
    @PostMapping("/verify")
    public AuthDtos.AuthResponse verify(@Valid @RequestBody OtpDtos.VerifyRequest request) {
        return authService.verifyEmail(request.email(), request.code());
    }

    /** 200 with the masked address and countdowns - identical for known and unknown mailboxes. */
    @PostMapping("/resend")
    public OtpDtos.OtpIssued resend(@Valid @RequestBody OtpDtos.ResendRequest request) {
        return authService.resendVerification(request.email());
    }
}
