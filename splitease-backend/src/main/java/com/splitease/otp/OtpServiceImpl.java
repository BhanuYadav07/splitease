package com.splitease.otp;

import com.splitease.exception.InvalidRequestException;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;
import java.util.Map;
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

    @Value("${brevo.api-key}")
    private String brevoApiKey;

    @Value("${brevo.from}")
    private String fromEmail;
    // ---------------------------------------------------------------- send

    @Transactional
    public OtpDtos.OtpIssued sendOtp(String rawEmail) {
        String email = normalize(rawEmail);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRequestException("Account not found."));
        assertNotLocked(user);
        return issueFor(user, true);
    }


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
        } catch (RestClientException | IllegalStateException e) {

            log.error("Brevo email sending failed for: {}", email, e);

            if (strictMail) {
                throw new InvalidRequestException(
                        "Failed to send verification email. Please try again."
                );
            }
        }
        return payload(user, otp, now);
    }

    // -------------------------------------------------------------- verify

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

        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IllegalStateException("Brevo API key is not configured.");
        }

        if (fromEmail == null || fromEmail.isBlank() || !fromEmail.contains("@")) {
            throw new IllegalStateException("Brevo sender email is not configured.");
        }

        Map<String, Object> requestBody = Map.of(
                "sender", Map.of(
                        "name", "SplitEase",
                        "email", fromEmail
                ),
                "to", List.of(
                        Map.of("email", to)
                ),
                "subject", "SplitEase verification code",
                "textContent",
                "Welcome to SplitEase!\n\n"
                        + "Your verification code is: " + code + "\n\n"
                        + "It expires in " + expiryMinutes + " minute(s).\n\n"
                        + "If you did not ask for this code you can ignore this email."
        );

        RestClient.create("https://api.brevo.com")
                .post()
                .uri("/v3/smtp/email")
                .header("api-key", brevoApiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();
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
