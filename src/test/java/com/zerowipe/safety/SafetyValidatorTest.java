package com.zerowipe.safety;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zerowipe.config.ZeroWipeProperties;
import com.zerowipe.device.BusType;
import com.zerowipe.device.DeviceDiscoveryService;
import com.zerowipe.device.MediaType;
import com.zerowipe.device.PhysicalDevice;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SafetyValidatorTest {

    private final DeviceDiscoveryService deviceDiscoveryService = mock(DeviceDiscoveryService.class);
    private final ZeroWipeProperties liveModeOn = properties(true);
    private final ZeroWipeProperties liveModeOff = properties(false);

    // ---------------------------------------------------------------
    // requireNoJobRunning
    // ---------------------------------------------------------------

    @Test
    void requireNoJobRunningThrowsWhenAJobIsRunning() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        SafetyCheckException e =
                assertThrows(SafetyCheckException.class, () -> validator.requireNoJobRunning(true));
        assertEquals(
                "Another sanitization job is already running; only one job may run at a time.", e.getMessage());
    }

    @Test
    void requireNoJobRunningPassesWhenNoJobIsRunning() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertDoesNotThrow(() -> validator.requireNoJobRunning(false));
    }

    // ---------------------------------------------------------------
    // requireLiveModeEnabled
    // ---------------------------------------------------------------

    @Test
    void requireLiveModeEnabledThrowsWhenDisabled() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOff);

        assertThrows(SafetyCheckException.class, validator::requireLiveModeEnabled);
    }

    @Test
    void requireLiveModeEnabledPassesWhenEnabled() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertDoesNotThrow(validator::requireLiveModeEnabled);
    }

    // ---------------------------------------------------------------
    // requireExistingDevice
    // ---------------------------------------------------------------

    @Test
    void requireExistingDeviceThrowsWhenNoSuchDisk() {
        when(deviceDiscoveryService.discoverDevice(99)).thenReturn(Optional.empty());
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        SafetyCheckException e =
                assertThrows(SafetyCheckException.class, () -> validator.requireExistingDevice(99));
        assertEquals("No physical drive found with disk number 99.", e.getMessage());
    }

    @Test
    void requireExistingDeviceReturnsTheDeviceWhenFound() {
        PhysicalDevice device = device(0, "SN123", false);
        when(deviceDiscoveryService.discoverDevice(0)).thenReturn(Optional.of(device));
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertEquals(device, validator.requireExistingDevice(0));
    }

    // ---------------------------------------------------------------
    // requireNotSystemDisk
    // ---------------------------------------------------------------

    @Test
    void requireNotSystemDiskThrowsForTheSystemDisk() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertThrows(
                SafetyCheckException.class, () -> validator.requireNotSystemDisk(device(0, "SN123", true)));
    }

    @Test
    void requireNotSystemDiskPassesForANonSystemDisk() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertDoesNotThrow(() -> validator.requireNotSystemDisk(device(1, "SN123", false)));
    }

    // ---------------------------------------------------------------
    // requireSerialConfirmed
    // ---------------------------------------------------------------

    @Test
    void requireSerialConfirmedThrowsWhenNull() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertThrows(
                SafetyCheckException.class,
                () -> validator.requireSerialConfirmed(device(0, "SN123", false), null));
    }

    @Test
    void requireSerialConfirmedThrowsWhenBlank() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertThrows(
                SafetyCheckException.class,
                () -> validator.requireSerialConfirmed(device(0, "SN123", false), "   "));
    }

    @Test
    void requireSerialConfirmedThrowsOnMismatch() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertThrows(
                SafetyCheckException.class,
                () -> validator.requireSerialConfirmed(device(0, "SN123", false), "SN999"));
    }

    @Test
    void requireSerialConfirmedPassesOnExactMatch() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertDoesNotThrow(() -> validator.requireSerialConfirmed(device(0, "SN123", false), "SN123"));
    }

    @Test
    void requireSerialConfirmedTrimsSurroundingWhitespace() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertDoesNotThrow(() -> validator.requireSerialConfirmed(device(0, "SN123", false), "  SN123  "));
    }

    // ---------------------------------------------------------------
    // validateForLiveSanitization - ordering and happy path
    // ---------------------------------------------------------------

    @Test
    void validateForLiveSanitizationReturnsTheDeviceWhenEveryCheckPasses() {
        PhysicalDevice device = device(0, "SN123", false);
        when(deviceDiscoveryService.discoverDevice(0)).thenReturn(Optional.of(device));
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        PhysicalDevice result = validator.validateForLiveSanitization(0, "SN123", false);

        assertEquals(device, result);
    }

    @Test
    void validateForLiveSanitizationChecksJobRunningBeforeTouchingTheDeviceLookup() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertThrows(
                SafetyCheckException.class,
                () -> validator.validateForLiveSanitization(0, "SN123", true));

        verify(deviceDiscoveryService, never()).discoverDevice(0);
    }

    @Test
    void validateForLiveSanitizationChecksLiveModeBeforeTouchingTheDeviceLookup() {
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOff);

        assertThrows(
                SafetyCheckException.class,
                () -> validator.validateForLiveSanitization(0, "SN123", false));

        verify(deviceDiscoveryService, never()).discoverDevice(0);
    }

    @Test
    void validateForLiveSanitizationRejectsTheSystemDiskEvenWithACorrectSerial() {
        PhysicalDevice systemDisk = device(0, "SN123", true);
        when(deviceDiscoveryService.discoverDevice(0)).thenReturn(Optional.of(systemDisk));
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertThrows(
                SafetyCheckException.class,
                () -> validator.validateForLiveSanitization(0, "SN123", false));
    }

    @Test
    void validateForLiveSanitizationRejectsAWrongSerialForANonSystemDisk() {
        PhysicalDevice device = device(1, "SN123", false);
        when(deviceDiscoveryService.discoverDevice(1)).thenReturn(Optional.of(device));
        SafetyValidator validator = new SafetyValidator(deviceDiscoveryService, liveModeOn);

        assertThrows(
                SafetyCheckException.class,
                () -> validator.validateForLiveSanitization(1, "wrong-serial", false));
    }

    private static PhysicalDevice device(int diskNumber, String serialNumber, boolean isSystemDisk) {
        return new PhysicalDevice(
                diskNumber,
                "Test Model",
                serialNumber,
                "1.0",
                BusType.SATA,
                MediaType.HDD,
                500_000_000_000L,
                512,
                isSystemDisk,
                false);
    }

    private static ZeroWipeProperties properties(boolean allowLiveMode) {
        return new ZeroWipeProperties(allowLiveMode, "", "", List.of());
    }
}
