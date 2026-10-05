package com.splitease.security;

import com.splitease.user.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reads the authenticated principal out of the SecurityContext. Every
 * controller uses this instead of trusting a userId supplied by the client,
 * so requests can't act on behalf of another user (see functional
 * requirement 10 - Group-Level Authorization).
 */
@Component
public class CurrentUserProvider {

    public AuthenticatedUser getPrincipal() {
        return (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public User getCurrentUser() {
        return getPrincipal().getUser();
    }

    public UUID getCurrentUserId() {
        return getPrincipal().getUserId();
    }
}
