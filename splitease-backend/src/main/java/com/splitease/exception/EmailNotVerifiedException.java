package com.splitease.exception;

/**
 * Thrown by login when the password is right but the address was never confirmed.
 * The account may not log in until the emailed code is entered, so this is refused
 * with 403 rather than 401 - the credentials were fine, the account is simply not
 * finished.
 */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
