package com.splitease.otp;

import com.splitease.exception.InvalidRequestException;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Every emailed 6-digit code lives here - sign-up confirmation, resend, and the
 * password reset. One guess budget ({@code app_user.failed_attempts}) is shared by
 * wrong codes and wrong passwords, and when it runs out the account stops receiving
 * mail (and can no longer log in) for {@code app.otp.mail-lock-minutes}.
 *
 * <p>Nothing about a code is ever returned to the caller: the response only carries a
 * masked address and two countdowns. There is no "here is the code because the mail
 * server is down" shortcut - that would be a bypass of the very check this service is.</p>
 */
@Service
@RequiredArgsConstructor
public class OtpServiceImpl {

    /** Shown when a reset is attempted without a live code (also enumeration-safe). */
    public static final String NO_ACTIVE_CODE =
            "No active verification code for this account. Request a new one.";

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

    private static final DateTimeFormatter LOCK_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu 'at' HH:mm", Locale.ENGLISH);

    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.expiry-minutes:5}")
    private int expiryMinutes;

    /** Wrong codes / passwords allowed before the pause. */
    @Value("${app.otp.max-attempts:3}")
    private int maxAttempts;

    /** How long the account is paused once the budget runs out. */
    @Value("${app.otp.mail-lock-minutes:15}")
    private int mailLockMinutes;

    /** Shortest gap between two emails to the same address. */
    @Value("${app.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    // ---------------------------------------------------------------- send

    /**
     * Issues a code for an account that is known to exist (register, and the
     * authenticated resend). A failure to reach the mailbox surfaces as a 400 so
     * sign-up does not silently leave an account nobody can confirm.
     */
    @Transactional
    public OtpDtos.OtpIssued sendOtp(String rawEmail) {
        String email = normalize(rawEmail);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRequestException("Account not found."));
        assertNotLocked(user);
        return issueFor(user, true);
    }

    /**
     * Enumeration-safe resend: the answer is identical whether or not the address has
     * an account, whether or not it is paused, and whether or not the mail went out.
     * Only an account that exists and is not paused actually receives a new code.
     */
    @Transactional
    public OtpDtos.OtpIssued resendVerification(String rawEmail) {
        String email = normalize(rawEmail);
        User user = userRepository.findByEmail(email).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (user == null || isTemporarilyLocked(user)) {
            return genericPayload(email, now);
        }
        return issueFor(user, false);
    }

    /**
     * Step 1 of a password reset. Like {@link #resendVerification}, the caller cannot
     * tell a registered address from an unknown one.
     */
    @Transactional
    public OtpDtos.ResetCodeIssued sendPasswordResetCode(String rawEmail) {
        String email = normalize(rawEmail);
        User user = userRepository.findByEmail(email).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        OtpDtos.OtpIssued issued = (user == null || isTemporarilyLocked(user))
                ? genericPayload(email, now)
                : issueFor(user, false);
        return new OtpDtos.ResetCodeIssued(
                issued.maskedEmail(), issued.expiresAt(), issued.expiresInSeconds(),
                issued.resendAvailableInSeconds(), issued.attemptsLeft());
    }

    /**
     * Shared issue path. Within the resend cooldown the live code is described again
     * instead of a new one being sent, so hammering "resend" cannot be turned into a
     * mail flood. A wrong or expired code still spends one of the account's guesses -
     * see {@link #verifyOtp}.
     */
    private OtpDtos.OtpIssued issueFor(User user, boolean strictMail) {
        String email = normalize(user.getEmail());
        LocalDateTime now = LocalDateTime.now();

        Optional<Otp> latest = otpRepository.findFirstByEmailOrderByCreatedAtDesc(email);
        if (latest.isPresent() && latest.get().getCreatedAt() != null
                && latest.get().getCreatedAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            return payload(user, latest.get(), now);
        }

        otpRepository.deleteExpiredOtps(now);
        otpRepository.invalidateOtpsForEmail(email);

        Otp otp = otpRepository.save(Otp.builder()
                .email(email)
                .code(generateCode())
                .expiresAt(now.plusMinutes(expiryMinutes))
                .build());

        try {
            sendEmail(email, otp.getCode());
        } catch (MailException e) {
            log.error("Failed to send verification email to {}: {}", email, e.getMessage());
            if (strictMail) {
                // Rolls back the caller's transaction (sign-up keeps the account only
                // if a code actually went out).
                throw new InvalidRequestException("Failed to send verification email. Please try again.");
            }
        }
        return payload(user, otp, now);
    }

    // -------------------------------------------------------------- verify

    /**
     * The sign-up / resend confirmation. A correct code verifies the mailbox and clears
     * the guess budget; a wrong one spends a guess and, at the limit, starts the pause.
     * {@code noRollbackFor} lets that spent guess commit even though the method ends by
     * throwing.
     */
    @Transactional(noRollbackFor = InvalidRequestException.class)
    public User verifyOtp(String rawEmail, String code) {
        String email = normalize(rawEmail);
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRequestException("Invalid or expired verification code."));
        assertNotLocked(user);

        Optional<Otp> valid = otpRepository.findValidOtp(email, submitted(code), now);
        if (valid.isPresent()) {
            Otp otp = valid.get();
            otp.setIsUsed(true);
            otpRepository.save(otp);
            user.setEmailVerified(true);
            user.setFailedAttempts(0);
            user.setMailBlockedUntil(null);
            return userRepository.save(user);
        }

        int left = recordFailedAttempt(user);
        if (left <= 0) {
            throw blocked(user);
        }
        throw new InvalidRequestException(
                "Invalid or expired verification code. " + left + " attempt(s) left.");
    }

    /**
     * The password-reset confirmation. Verifies and *spends* the code, but leaves the
     * account's verified flag to the caller (the reset marks it verified too). A wrong
     * code costs a guess even when the address turns out to be nobody's.
     */
    @Transactional(noRollbackFor = InvalidRequestException.class)
    public void consumeCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            assertNotLocked(user);
        }

        Optional<Otp> valid = otpRepository.findValidOtp(email, submitted(code), now);
        if (valid.isPresent()) {
            Otp otp = valid.get();
            otp.setIsUsed(true);
            otpRepository.save(otp);
            if (user != null) {
                user.setFailedAttempts(0);
                user.setMailBlockedUntil(null);
                userRepository.save(user);
            }
            return;
        }

        if (user == null) {
            throw new InvalidRequestException(NO_ACTIVE_CODE);
        }
        int left = recordFailedAttempt(user);
        if (left <= 0) {
            throw blocked(user);
        }
        throw new InvalidRequestException(
                "Invalid or expired verification code. " + left + " attempt(s) left.");
    }

    // ------------------------------------------------------- guess budget

    /** Spends one guess; returns how many are left, or 0 once the pause has started. */
    @Transactional
    public int recordFailedAttempt(User user) {
        int used = user.getFailedAttempts() + 1;
        if (used >= maxAttempts) {
            user.setFailedAttempts(0);
            user.setMailBlockedUntil(LocalDateTime.now().plusMinutes(mailLockMinutes));
            otpRepository.invalidateOtpsForEmail(normalize(user.getEmail()));
            userRepository.save(user);
            log.warn("{} used all {} attempts - no mail and no login until {}",
                    user.getEmail(), maxAttempts, user.getMailBlockedUntil());
            return 0;
        }
        user.setFailedAttempts(used);
        userRepository.save(user);
        return maxAttempts - used;
    }

    /** A good password (or a good code) resets the budget and lifts a spent pause. */
    @Transactional
    public void clearFailedAttempts(User user) {
        if (user.getFailedAttempts() > 0 || user.getMailBlockedUntil() != null) {
            user.setFailedAttempts(0);
            user.setMailBlockedUntil(null);
            userRepository.save(user);
        }
    }

    /** True while the account is in the post-budget pause. */
    public boolean isTemporarilyLocked(User user) {
        return user.getMailBlockedUntil() != null
                && user.getMailBlockedUntil().isAfter(LocalDateTime.now());
    }

    /** Human wording for the pause, including when it lifts. */
    public String lockMessage(User user) {
        return "Too many incorrect attempts. Try again after "
                + LOCK_FORMAT.format(user.getMailBlockedUntil()) + ".";
    }

    private void assertNotLocked(User user) {
        if (isTemporarilyLocked(user)) {
            throw new InvalidRequestException(lockMessage(user));
        }
    }

    private int attemptsLeft(User user) {
        if (isTemporarilyLocked(user)) {
            return 0;
        }
        return Math.max(0, maxAttempts - user.getFailedAttempts());
    }

    // ------------------------------------------------------------- helpers

    private OtpDtos.OtpIssued payload(User user, Otp otp, LocalDateTime now) {
        long expiresIn = Math.max(0, ChronoUnit.SECONDS.between(now, otp.getExpiresAt()));
        LocalDateTime issuedAt = otp.getExpiresAt().minusMinutes(expiryMinutes);
        long resendIn = Math.max(0, ChronoUnit.SECONDS.between(now, issuedAt.plusSeconds(resendCooldownSeconds)));
        return new OtpDtos.OtpIssued(
                mask(otp.getEmail()), instant(otp.getExpiresAt()), expiresIn, resendIn, attemptsLeft(user));
    }

    /** The answer for an address with no account, a paused account, or a failed send. */
    private OtpDtos.OtpIssued genericPayload(String email, LocalDateTime now) {
        return new OtpDtos.OtpIssued(
                mask(email),
                instant(now.plusMinutes(expiryMinutes)),
                expiryMinutes * 60L,
                resendCooldownSeconds,
                0);
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        // spring.mail.username may still hold the {Google_GMAIL} placeholder; only
        // echo it as the sender when it actually looks like an address.
        if (fromEmail != null && fromEmail.contains("@")) {
            message.setFrom(fromEmail);
        }
        message.setTo(to);
        message.setSubject("SplitEase verification code");
        message.setText("Welcome to SplitEase!\n\n"
                + "Your verification code is: " + code + "\n\n"
                + "It expires in " + expiryMinutes + " minute(s).\n\n"
                + "If you did not ask for this code you can ignore this email.");
        mailSender.send(message);
    }

    private String generateCode() {
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private InvalidRequestException blocked(User user) {
        return new InvalidRequestException(lockMessage(user));
    }

    private String submitted(String code) {
        return code == null ? "" : code.trim();
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    /** tarun@gmail.com -> t***n@gmail.com */
    private String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) {
            return "your email address";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
