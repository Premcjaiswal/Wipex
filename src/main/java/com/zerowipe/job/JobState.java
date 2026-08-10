package com.zerowipe.job;

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
