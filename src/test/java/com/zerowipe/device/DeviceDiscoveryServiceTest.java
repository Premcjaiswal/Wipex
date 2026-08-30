package com.zerowipe.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeviceDiscoveryServiceTest {

    private final PowerShellQueryService powerShell = mock(PowerShellQueryService.class);
    private final SystemDiskResolver systemDiskResolver = mock(SystemDiskResolver.class);
    private final DeviceDiscoveryService service = new DeviceDiscoveryService(powerShell, systemDiskResolver);

    @Test
    void buildsAPhysicalDeviceFromGetPhysicalDiskOutput() {
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(
                        0, "FastDrive 9000", "SN123", "1.0", "SATA", "HDD", 500_000_000_000L, 512)));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        List<PhysicalDevice> devices = service.discoverDevices();

        assertEquals(1, devices.size());
        PhysicalDevice device = devices.get(0);
        assertEquals(0, device.diskNumber());
        assertEquals("FastDrive 9000", device.model());
        assertEquals("SN123", device.serialNumber());
        assertEquals("1.0", device.firmwareRevision());
        assertEquals(BusType.SATA, device.busType());
        assertEquals(MediaType.HDD, device.mediaType());
        assertEquals(500_000_000_000L, device.capacityBytes());
        assertEquals(512, device.bytesPerSector());
        assertFalse(device.isSystemDisk());
        assertFalse(device.isRemovable());
    }

    @Test
    void marksTheDiskListedBySystemDiskResolverAsTheSystemDisk() {
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "Model", "SN0", "1.0", "SATA", "HDD", 1024L, 512)));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of(0));

        PhysicalDevice device = service.discoverDevices().get(0);

        assertTrue(device.isSystemDisk());
    }

    @Test
    void diskWithNullDeviceIdIsSkipped() {
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(null, "Weird", "SN?", "1.0", "SATA", "HDD", 1024L, 512)));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        List<PhysicalDevice> devices = service.discoverDevices();

        assertTrue(devices.isEmpty());
    }

    @Test
    void usbBusTypeIsTreatedAsRemovable() {
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "Model", "SN0", "1.0", "USB", "SSD", 2048L, 4096)));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        PhysicalDevice device = service.discoverDevices().get(0);

        assertEquals(BusType.USB, device.busType());
        assertTrue(device.isRemovable());
    }

    @Test
    void nvmeBusTypeOverridesReportedMediaType() {
        // Get-PhysicalDisk reports NVMe drives' MediaType as plain "SSD" -
        // NVMe media type is derived from bus type, not the MediaType field.
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "Model", "SN0", "1.0", "NVMe", "SSD", 2048L, 512)));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        PhysicalDevice device = service.discoverDevices().get(0);

        assertEquals(BusType.NVME, device.busType());
        assertEquals(MediaType.NVME, device.mediaType());
    }

    @Test
    void unrecognisedMediaTypeMapsToUnknown() {
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "Model", "SN0", "1.0", "SATA", "Unspecified", 1024L, 512)));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        PhysicalDevice device = service.discoverDevices().get(0);

        assertEquals(MediaType.UNKNOWN, device.mediaType());
    }

    @Test
    void discoverDeviceReturnsEmptyOptionalWhenNoMatchingDiskExists() {
        when(powerShell.queryPhysicalDisks()).thenReturn(List.of());
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        Optional<PhysicalDevice> result = service.discoverDevice(99);

        assertTrue(result.isEmpty());
    }

    @Test
    void discoverDeviceReturnsTheMatchingDisk() {
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(
                        new PhysicalDiskInfo(0, "First", "SN0", "1.0", "SATA", "HDD", 1024L, 512),
                        new PhysicalDiskInfo(1, "Second", "SN1", "1.0", "USB", "SSD", 2048L, 4096)));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        Optional<PhysicalDevice> result = service.discoverDevice(1);

        assertTrue(result.isPresent());
        assertEquals("Second", result.get().model());
        assertEquals(BusType.USB, result.get().busType());
    }
}
