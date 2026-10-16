package com.splitease.auth;

import com.splitease.exception.AuthenticationFailedException;
import com.splitease.exception.ConflictException;
import com.splitease.exception.EmailNotVerifiedException;
import com.splitease.exception.InvalidRequestException;
import com.splitease.otp.OtpDtos;
import com.splitease.otp.OtpServiceImpl;
import com.splitease.security.AuthenticatedUser;
import com.splitease.user.User;
import com.splitease.user.UserDtos;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpServiceImpl otpService;

    /**
     * Creates the account and emails a 5-minute code. No token comes back: sign-up is a
     * gate, and the only thing an unconfirmed account may do is prove its mailbox via
     * {@code /api/otp/verify}. The dashboard opens with the session that verification
     * hands back - never before.
     */
    @Transactional
    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = userRepository.save(
                new User(request.name().trim(), email, passwordEncoder.encode(request.password())));

        // Strict: if the code cannot be sent the whole sign-up rolls back, so no account
        // is created that the owner could never confirm.
        OtpDtos.OtpIssued verification = otpService.sendOtp(email);
        return new AuthDtos.RegisterResponse(UserDtos.UserResponse.from(user), verification);
    }

    /**
     * Confirms the mailbox and, only now, signs the user in. The code itself is the
     * credential, so this works without a session - which is exactly what lets an
     * account that never finished sign-up (or that just changed its address) get in.
     */
    public AuthDtos.AuthResponse verifyEmail(String email, String code) {
        User user = otpService.verifyOtp(email, code);
        return new AuthDtos.AuthResponse(
                jwtService.generateToken(new AuthenticatedUser(user)), UserDtos.UserResponse.from(user));
    }

    /** Enumeration-safe: the answer names nothing about whether the address is registered. */
    public OtpDtos.OtpIssued resendVerification(String email) {
        return otpService.resendVerification(email);
    }

    /**
     * Starts a password reset. The answer is deliberately the same whether or not the
     * address has an account: the caller learns that something <em>may</em> be on its way,
     * and never who is registered.
     */
    public OtpDtos.ResetCodeIssued forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        return otpService.sendPasswordResetCode(request.email());
    }

    /**
     * Finishes it: the emailed code stands in for the old password and, like login, a
     * fresh session goes back with the answer. A successful code also marks the address
     * verified - the mailbox just proved itself.
     *
     * <p>{@code noRollbackFor} for the same reason as {@link OtpServiceImpl#consumeCode}:
     * a wrong code must still spend one of the guesses, and that write cannot commit on
     * its own once this method has opened the transaction.</p>
     */
    @Transactional(noRollbackFor = InvalidRequestException.class)
    public AuthDtos.AuthResponse resetPassword(AuthDtos.ResetPasswordRequest request) {
        String email = normalize(request.email());
        // The code is checked (and spent) first, so a wrong one costs a guess even if the
        // address turns out to be nobody's.
        otpService.consumeCode(email, request.code());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRequestException(OtpServiceImpl.NO_ACTIVE_CODE));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        return new AuthDtos.AuthResponse(
                jwtService.generateToken(new AuthenticatedUser(saved)), UserDtos.UserResponse.from(saved));
    }

    /**
     * Only a confirmed account gets a session. A correct password on an unverified
     * address re-sends the code and is refused with 403 ({@link EmailNotVerifiedException}),
     * so unverified callers are pushed to the confirmation step instead of into the app.
     * A wrong password spends a guess from the shared budget - {@code app.otp.max-attempts}
     * wrong passwords, like wrong codes, pause the account for
     * {@code app.otp.mail-lock-minutes} (no mail, and no more login attempts until it lifts).
     *
     * <p>Not {@code @Transactional} on purpose: each step writes on its own, so the failed
     * attempt recorded below survives the exception this method ends up throwing.</p>
     */
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String email = normalize(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password."));

        if (otpService.isTemporarilyLocked(user)) {
            throw new AuthenticationFailedException(otpService.lockMessage(user));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            otpService.recordFailedAttempt(user);
            throw new AuthenticationFailedException("Invalid email or password.");
        }

        if (!user.isEmailVerified()) {
            // Right password, unfinished account: hand out a fresh code, but no session.
            otpService.resendVerification(email);
            throw new EmailNotVerifiedException(
                    "Please verify your email address before logging in. We sent a new code to your inbox.");
        }

        otpService.clearFailedAttempts(user);
        User saved = userRepository.save(user);
        return new AuthDtos.AuthResponse(
                jwtService.generateToken(new AuthenticatedUser(saved)), UserDtos.UserResponse.from(saved));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
