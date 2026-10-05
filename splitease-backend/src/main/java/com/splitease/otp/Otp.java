package com.splitease.otp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One emailed verification code, stored in the {@code otps} table.
 *
 * <p>Deliberately plain: a row is written at sign-up and on every resend, and it
 * stops working the moment it is verified, replaced by a newer code, or its
 * window runs out. There is no attempt counter here - the guess budget belongs to
 * the account ({@code app_user.failed_attempts}), not to a single row.</p>
 */
@Entity
@Table(name = "otps", indexes = @Index(name = "idx_otps_email", columnList = "email"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Otp {

    /** Digits per code - 6 is the familiar "check your inbox" length. */
    public static final int CODE_LENGTH = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Text, not a number, so a leading zero would survive ("007431" is not 7431). */
    @Column(name = "code", nullable = false, length = CODE_LENGTH)
    private String code;

    /** True once verified, or once a newer code / the mail pause has retired it. */
    @Column(name = "is_used")
    @Builder.Default
    private Boolean isUsed = false;

    /** issued-at plus app.otp.expiry-minutes. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** True once the 5-minute window has passed. */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
