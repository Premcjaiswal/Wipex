package com.zerowipe.safety;

/**
 * Thrown when a sanitization request fails one of the backend's
 * independent safety checks - system disk, live mode disabled, unknown or
 * changed device, serial mismatch, or a job already running. The message
 * always names exactly which check failed; there is no silent partial
 * failure.
 */
public class SafetyCheckException extends RuntimeException {

    public SafetyCheckException(String message) {
        super(message);
    }
}
