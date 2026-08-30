package com.zerowipe.policy;

/**
 * Reasons the policy engine can refuse a sanitization method or job. Every
 * constant carries a human-readable message suitable for display to the
 * operator - refusals must always be explained, never silent.
 */
public enum RefusalReason {
    SYSTEM_DISK(
            "Refused: this disk contains the active system volume or boot files."),
    RAID_VIRTUAL_DISK(
            "Refused: this disk is a RAID/virtual volume, not physical media - no sanitization claim is valid."),
    USB_BRIDGE_UNRELIABLE(
            "Refused: this device is attached through a USB bridge chip, whose media type reporting is unreliable."),
    PURGE_NOT_SUPPORTED_IN_THIS_BUILD(
            "Refused: Purge requires device-level commands (NVMe Sanitize, ATA Secure Erase, Opal revert) that "
                    + "are out of scope for this build; only Clear-level methods are available."),
    MEDIA_TYPE_UNKNOWN(
            "Refused: the media type of this device could not be determined."),
    LIVE_MODE_DISABLED(
            "Refused: live mode is disabled in configuration (zerowipe.allow-live-mode)."),
    SERIAL_NOT_IN_ALLOWLIST(
            "Refused: this device's serial number is not present in the configured test-disk allowlist.");

    private final String message;

    RefusalReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
