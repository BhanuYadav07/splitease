package com.splitease.auth;

import com.splitease.exception.AuthenticationFailedException;
import com.splitease.exception.ConflictException;
import com.splitease.exception.InvalidRequestException;
import com.splitease.otp.OtpDtos;
import com.splitease.otp.OtpService;
import com.splitease.security.AuthenticatedUser;
import com.splitease.user.User;
import com.splitease.user.UserDtos;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;

    /**
     * Creates the account and emails a 5-minute verification code. The token that comes
     * back is shaped exactly like login's, but it exists for one purpose: the client
     * stores it and lands on the confirmation step, which needs a session to verify and
     * resend with. The dashboard opens only after that code is accepted.
     */
    @Transactional
    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = userRepository.save(new User(request.name(), request.email(), passwordEncoder.encode(request.password())));

        OtpDtos.OtpIssued verification = otpService.sendOtp(user);
        String token = jwtService.generateToken(new AuthenticatedUser(user));

        return new AuthDtos.RegisterResponse(token, UserDtos.UserResponse.from(user), verification);
    }

    /**
     * Starts a password reset. The answer is deliberately the same whether or not the
     * address has an account (see {@link OtpService#sendPasswordResetCode}): the caller
     * learns that something <em>may</em> be on its way, and never who is registered.
     */
    public OtpDtos.ResetCodeIssued forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        return otpService.sendPasswordResetCode(request.email());
    }

    /**
     * Finishes it: the emailed code stands in for the old password and, like login, a
     * fresh session goes back with the answer so the user lands inside the app instead
     * of on the login screen. A successful code also marks the address verified - the
     * mailbox just proved itself - so someone who never confirmed their sign-up email
     * comes back with a verified account.
     *
     * <p>{@code noRollbackFor}, again, and for the same reason as
     * {@link OtpService#verifyOtp}: a wrong code must still spend one of the five
     * guesses, and that write happens inside {@code OtpService.consumeCode}, an inner
     * {@code @Transactional} that cannot commit on its own once this method has opened
     * the transaction.</p>
     */
    @Transactional(noRollbackFor = InvalidRequestException.class)
    public AuthDtos.AuthResponse resetPassword(AuthDtos.ResetPasswordRequest request) {
        // The code is checked (and spent) first, so a wrong one costs a guess even if
        // the address turns out to be nobody's.
        otpService.consumeCode(request.email(), request.code());
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidRequestException(OtpService.NO_ACTIVE_CODE));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        User saved = userRepository.save(user);
        return new AuthDtos.AuthResponse(jwtService.generateToken(new AuthenticatedUser(saved)),
                UserDtos.UserResponse.from(saved));
    }

    /**
     * A good password clears the guess budget; a bad one spends a guess from the
     * same five that wrong verification codes use, so five wrong passwords also
     * stop all mail to the account for three days (see OtpServiceImpl).
     */
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            otpService.recordFailedAttempt(user);
            throw new AuthenticationFailedException("Invalid email or password.");
        }

        otpService.clearFailedAttempts(user);
        String token = jwtService.generateToken(new AuthenticatedUser(user));
        return new AuthDtos.AuthResponse(token, UserDtos.UserResponse.from(user));
    }
}
