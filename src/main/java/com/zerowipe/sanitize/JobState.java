package com.zerowipe.sanitize;

/**
 * States in the sanitization job state machine.
 */
public enum JobState {
    CREATED,
    CONFIRMED,
    PREPARING,
    RUNNING,
    VERIFYING,
    COMPLETED,
    FAILED,
    ABORTED
}
