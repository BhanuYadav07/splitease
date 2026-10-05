package com.splitease.otp;

import com.splitease.exception.InvalidRequestException;
import com.splitease.user.User;
import com.splitease.user.UserDtos;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

    private static final DateTimeFormatter PAUSE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu 'at' HH:mm", Locale.ENGLISH);

    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.mail-lock-days:3}")
    private int mailLockDays;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ---------- send ----------

    @Override
    @Transactional
    public OtpDtos.OtpIssued sendOtp(User user) {
        String email = normalize(user.getEmail());
        Otp otp = issue(email, user);
        return new OtpDtos.OtpIssued(
                email,
                instant(otp.getExpiresAt()),
                expiryMinutes * 60L,
                attemptsLeft(user));
    }

    @Override
    @Transactional
    public OtpDtos.ResetCodeIssued sendPasswordResetCode(String rawEmail) {
        String email = normalize(rawEmail);
        User account = userRepository.findByEmail(email).orElse(null);

        // Unknown address: same response, no email sent
        Otp otp = issue(email, account);
        return new OtpDtos.ResetCodeIssued(
                email,
                instant(otp.getExpiresAt()),
                expiryMinutes * 60L);
    }

    private Otp issue(String email, User account) {
        LocalDateTime now = LocalDateTime.now();

        if (account != null && account.getMailBlockedUntil() != null) {
            if (account.getMailBlockedUntil().isAfter(now)) {
                throw blocked(account);
            }
            // Pause served: start clean
            account.setMailBlockedUntil(null);
            account.setFailedAttempts(0);
        }

        otpRepository.deleteExpiredOtps(now);
        otpRepository.invalidateOtpsForEmail(email);

        Otp otp = otpRepository.save(Otp.builder()
                .email(email)
                .code(generateCode())
                .expiresAt(now.plusMinutes(expiryMinutes))
                .build());

        if (account != null) {
            sendEmail(email, otp.getCode());
        }
        return otp;
    }

    // ---------- verify ----------

    @Override
    @Transactional(noRollbackFor = InvalidRequestException.class)
    public UserDtos.UserResponse verifyOtp(User user, String code) {
        return UserDtos.UserResponse.from(consume(normalize(user.getEmail()), user, code));
    }

    @Override
    @Transactional(noRollbackFor = InvalidRequestException.class)
    public void consumeCode(String email, String code) {
        String address = normalize(email);
        consume(address, userRepository.findByEmail(address).orElse(null), code);
    }

    private User consume(String email, User account, String code) {
        LocalDateTime now = LocalDateTime.now();
        String submitted = code == null ? "" : code.trim();

        if (account != null && account.getMailBlockedUntil() != null
                && account.getMailBlockedUntil().isAfter(now)) {
            otpRepository.invalidateOtpsForEmail(email);
            throw blocked(account);
        }

        var valid = otpRepository.findValidOtp(email, submitted, now);
        if (valid.isPresent()) {
            Otp otp = valid.get();
            otp.setIsUsed(true);
            otpRepository.save(otp);

            if (account == null) {
                return null;
            }
            account.setEmailVerified(true);
            account.setFailedAttempts(0);
            return userRepository.save(account);
        }

        // Wrong or expired code
        if (account == null) {
            throw new InvalidRequestException("Invalid or expired verification code.");
        }
        int left = recordFailedAttempt(account);
        if (left <= 0) {
            throw blocked(account);
        }
        throw new InvalidRequestException(
                "Invalid or expired verification code. " + left + " attempt(s) left.");
    }

    // ---------- attempt limit ----------

    @Override
    @Transactional
    public int recordFailedAttempt(User user) {
        int used = user.getFailedAttempts() + 1;
        if (used >= maxAttempts) {
            user.setFailedAttempts(0);
            user.setMailBlockedUntil(LocalDateTime.now().plusDays(mailLockDays));
            otpRepository.invalidateOtpsForEmail(normalize(user.getEmail()));
            userRepository.save(user);
            log.warn("{} used all {} attempts - no account emails until {}",
                    user.getEmail(), maxAttempts, user.getMailBlockedUntil());
            return 0;
        }
        user.setFailedAttempts(used);
        userRepository.save(user);
        return maxAttempts - used;
    }

    @Override
    @Transactional
    public void clearFailedAttempts(User user) {
        if (user.getFailedAttempts() > 0) {
            user.setFailedAttempts(0);
            userRepository.save(user);
        }
    }

    // ---------- helpers ----------

    private int attemptsLeft(User account) {
        if (account.getMailBlockedUntil() != null
                && account.getMailBlockedUntil().isAfter(LocalDateTime.now())) {
            return 0;
        }
        return Math.max(0, maxAttempts - account.getFailedAttempts());
    }

    private InvalidRequestException blocked(User account) {
        return new InvalidRequestException(
                "Too many incorrect attempts. Emails to this account are paused until "
                        + PAUSE_FORMAT.format(account.getMailBlockedUntil()) + ".");
    }

    private String generateCode() {
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private void sendEmail(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("SplitEase verification code");
            message.setText("Welcome to SplitEase!\n\n"
                    + "Your verification code is: " + code + "\n\n"
                    + "It expires in " + expiryMinutes + " minute(s).\n\n"
                    + "If you did not ask for this code you can ignore this email.");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new InvalidRequestException("Failed to send verification email. Please try again.");
        }
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "***" + domain;
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}