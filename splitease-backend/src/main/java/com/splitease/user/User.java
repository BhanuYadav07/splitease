package com.splitease.user;

import com.splitease.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@ToString
public class User extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    /**
     * False until the user types the 6-digit code that was emailed at sign-up.
     * The account is usable while unverified (see OtpService) - being asked again
     * on the next login is the nudge.
     */
    @Column(nullable = false)
    private boolean emailVerified = false;

    /**
     * Wrong guesses - a bad verification code or a bad password - since the last
     * success. At {@code app.otp.max-attempts} (5) the account is put in the mail
     * pause below. Kept on the account, not on the code, because the budget has to
     * outlive any single code an attacker could otherwise just ask to be replaced.
     */
    @Column(nullable = false)
    private int failedAttempts = 0;

    /**
     * Set when the guess budget runs out: no mail is sent to this address until
     * then ({@code app.otp.mail-lock-days}, 3 days). Null, or a past timestamp,
     * means the account is not paused.
     */
    @Column
    private LocalDateTime mailBlockedUntil;

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
