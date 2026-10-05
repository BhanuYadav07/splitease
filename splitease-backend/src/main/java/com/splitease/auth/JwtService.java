package com.splitease.auth;

import com.splitease.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    /** User id claim: the id never changes, the email can. */
    private static final String USER_ID_CLAIM = "uid";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${splitease.jwt.secret}") String secret,
            @Value("${splitease.jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        var builder = Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey);
        if (userDetails instanceof AuthenticatedUser authenticatedUser) {
            builder.claim(USER_ID_CLAIM, authenticatedUser.getUserId().toString());
        }
        return builder.compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * The id the token was issued for, or null for tokens minted before the claim
     * existed (those fall back to the email subject).
     */
    public UUID extractUserId(String token) {
        String userId = extractClaim(token, claims -> claims.get(USER_ID_CLAIM, String.class));
        return userId == null ? null : UUID.fromString(userId);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
