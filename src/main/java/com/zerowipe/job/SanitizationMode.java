package com.zerowipe.job;

/**
 * Whether a job performs real destructive writes or only simulates them.
 * SIMULATION is the default everywhere in this system.
 */
public enum SanitizationMode {
    SIMULATION,
    LIVE
}
