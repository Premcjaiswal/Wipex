package com.zerowipe.safety;

import com.zerowipe.config.ZeroWipeProperties;
import com.zerowipe.device.DeviceDiscoveryService;
import com.zerowipe.device.PhysicalDevice;
import org.springframework.stereotype.Service;

/**
 * The backend's independent safety gate for live sanitization. The
 * frontend also blocks the system disk and requires serial confirmation,
 * but none of that can be trusted - every check here re-verifies against
 * the device as it actually is right now, not what the operator was shown
 * earlier.
 *
 * <p>Each check is a separate method so it can be tested and reasoned
 * about on its own; {@link #validateForLiveSanitization} runs all of them
 * in a fixed order and returns the validated device once every check has
 * passed. There is no {@code PolicyDecision}-style result object - a check
 * either passes or throws {@link SafetyCheckException} naming exactly why.
 *
 * <p>"Another job already running" is passed in by the caller rather than
 * looked up here, since job tracking doesn't exist yet - the orchestrator
 * built later will supply it.
 */
@Service
public class SafetyValidator {

    private final DeviceDiscoveryService deviceDiscoveryService;
    private final ZeroWipeProperties properties;

    public SafetyValidator(DeviceDiscoveryService deviceDiscoveryService, ZeroWipeProperties properties) {
        this.deviceDiscoveryService = deviceDiscoveryService;
        this.properties = properties;
    }

    /**
     * Runs every safety check for a live sanitization request, in order:
     * no job already running, live mode enabled, device exists, device is
     * not the system disk, operator-entered serial number matches.
     *
     * @param diskNumber the disk the operator selected
     * @param confirmedSerialNumber the serial number the operator typed to confirm the target
     * @param anotherJobRunning whether a sanitization job is already in progress
     * @return the validated device, safe to sanitize
     * @throws SafetyCheckException naming the first check that failed
     */
    public PhysicalDevice validateForLiveSanitization(
            int diskNumber, String confirmedSerialNumber, boolean anotherJobRunning) {
        requireNoJobRunning(anotherJobRunning);
        requireLiveModeEnabled();
        PhysicalDevice device = requireExistingDevice(diskNumber);
        requireNotSystemDisk(device);
        requireSerialConfirmed(device, confirmedSerialNumber);
        return device;
    }

    public void requireNoJobRunning(boolean anotherJobRunning) {
        if (anotherJobRunning) {
            throw new SafetyCheckException("Another sanitization job is already running; only one job may run at a time.");
        }
    }

    public void requireLiveModeEnabled() {
        if (!properties.allowLiveMode()) {
            throw new SafetyCheckException(
                    "Live mode is disabled (zerowipe.allow-live-mode is false); refusing to sanitize.");
        }
    }

    /**
     * Looks the device up fresh rather than trusting anything the operator
     * was shown earlier - this is also what catches a disk number that
     * never existed, and (combined with {@link #requireSerialConfirmed})
     * a device that was swapped out since it was selected.
     */
    public PhysicalDevice requireExistingDevice(int diskNumber) {
        return deviceDiscoveryService
                .discoverDevice(diskNumber)
                .orElseThrow(() -> new SafetyCheckException(
                        "No physical drive found with disk number " + diskNumber + "."));
    }

    public void requireNotSystemDisk(PhysicalDevice device) {
        if (device.isSystemDisk()) {
            throw new SafetyCheckException(
                    "Disk " + device.diskNumber() + " is the active system disk; refusing to sanitize it.");
        }
    }

    /**
     * Requires the operator to have typed the device's actual current
     * serial number, not merely confirmed with "yes". Comparing against a
     * freshly looked-up device (see {@link #requireExistingDevice}) is
     * also what catches the device having been swapped for a different one
     * since it was selected on screen 1.
     */
    public void requireSerialConfirmed(PhysicalDevice device, String confirmedSerialNumber) {
        if (confirmedSerialNumber == null || confirmedSerialNumber.isBlank()) {
            throw new SafetyCheckException(
                    "Serial number confirmation is required before sanitizing disk " + device.diskNumber() + ".");
        }
        if (!confirmedSerialNumber.trim().equals(device.serialNumber())) {
            throw new SafetyCheckException(
                    "Entered serial number does not match disk " + device.diskNumber() + "'s actual serial number.");
        }
    }
}
