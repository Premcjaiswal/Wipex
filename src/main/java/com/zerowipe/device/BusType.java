package com.zerowipe.device;

/**
 * Physical bus a storage device is attached through. RAID indicates a
 * virtual disk exposed by a controller, not physical media - no
 * sanitization claim is valid for it. USB indicates a bridge chip, whose
 * media type reporting is unreliable.
 */
public enum BusType {
    SATA,
    NVME,
    USB,
    RAID,
    SAS,
    UNKNOWN
}
