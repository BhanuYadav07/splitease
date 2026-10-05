package com.splitease.security;

import com.splitease.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER);
        if (authHeader == null || !authHeader.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(PREFIX.length());
        UUID userId;
        String email;
        try {
            userId = jwtService.extractUserId(token);
            email = userId == null ? jwtService.extractEmail(token) : null;
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if ((userId != null || email != null) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthenticatedUser userDetails = userId != null
                        ? userDetailsService.loadUserById(userId)
                        : userDetailsService.loadUserByUsername(email);

                boolean valid = userId != null
                        // Identity by id: the email recorded in the token may simply be
                        // out of date, because editing the profile is allowed to change it.
                        ? !jwtService.isTokenExpired(token)
                        : jwtService.isTokenValid(token, userDetails);

                if (valid) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (UsernameNotFoundException ex) {
                // The token is structurally valid but refers to a user that no longer
                // exists (the account was deleted). Treat it as unauthenticated instead
                // of letting the exception escape the filter and surface as a 500.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
