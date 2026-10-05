package com.splitease.otp;

import com.splitease.security.CurrentUserProvider;
import com.splitease.user.UserDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/verify")
    public UserDtos.UserResponse verify(@Valid @RequestBody OtpDtos.VerifyRequest request) {
        return otpService.verifyOtp(currentUserProvider.getCurrentUser(), request.code());
    }

    @PostMapping("/resend")
    public OtpDtos.OtpIssued resend() {
        return otpService.sendOtp(currentUserProvider.getCurrentUser());
    }
}
