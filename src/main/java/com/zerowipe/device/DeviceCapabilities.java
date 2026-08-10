package com.zerowipe.device;

/**
 * Capability and health data for a physical device. Fields are nullable
 * where the underlying bus or device does not reliably report them (for
 * example, SMART data is often unavailable through a USB bridge chip);
 * a null value must be surfaced as "unknown", never defaulted.
 */
public record DeviceCapabilities(
        boolean supportsTrim,
        Long reallocatedSectorCount,
        String smartHealthStatus,
        Integer wearPercentage,
        Long powerOnHours) {
}
