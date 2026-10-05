package com.splitease.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * All the SQL for the verification codes, and all of it fits on one screen:
 * one lookup that can let somebody in, one finder for the failure messages,
 * and the two bulk statements behind resends and housekeeping.
 */
@Repository
public interface OtpRepository extends JpaRepository<Otp, Long> {

    /** The one row that would verify this user: matching code, unused, still inside its window. */
    @Query("""
            SELECT o
            FROM Otp o
            WHERE o.email = :email
            AND o.code = :code
            AND o.isUsed = false
            AND o.expiresAt > :now
            ORDER BY o.createdAt DESC
            """)
    Optional<Otp> findValidOtp(String email, String code, LocalDateTime now);

    Optional<Otp> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Modifying
    @Transactional
    @Query("""
            UPDATE Otp o
            SET o.isUsed = true
            WHERE o.email = :email
            """)
    void invalidateOtpsForEmail(String email);

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM Otp o
            WHERE o.expiresAt < :now
            """)
    void deleteExpiredOtps(LocalDateTime now);
}
