package com.splitease.otp;

import com.splitease.user.User;
import com.splitease.user.UserDtos;

public interface OtpService {
    String NO_ACTIVE_CODE = "No active verification code for this account. Request a new one.";

    OtpDtos.OtpIssued sendOtp(User user);

    UserDtos.UserResponse verifyOtp(User user, String code);

    OtpDtos.ResetCodeIssued sendPasswordResetCode(String email);

    void consumeCode(String email, String code);

    int recordFailedAttempt(User user);

    void clearFailedAttempts(User user);
}
