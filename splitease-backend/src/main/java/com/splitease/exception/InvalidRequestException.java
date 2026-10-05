package com.splitease.exception;

/**
 * Thrown for domain validation failures: split sums that don't match the
 * total, invalid percentages, duplicate participants, non-positive amounts,
 * self-settlement, payer/participant not in group, etc.
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
