package com.splitease.exception;

/**
 * Thrown when an authenticated user tries to act on a group/expense/settlement
 * they are not a member of, or lacks permission for (e.g. editing another
 * user's expense, changing group ID in the URL to access a foreign group).
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
