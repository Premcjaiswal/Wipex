package com.zerowipe.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zerowipe.nativelayer.DeviceDescriptor;
import com.zerowipe.nativelayer.DriveGeometry;
import com.zerowipe.nativelayer.FakeNativeDeviceGateway;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeviceDiscoveryServiceTest {

    private final FakeNativeDeviceGateway gateway = new FakeNativeDeviceGateway();
    private final CapabilityDetector capabilityDetector = mock(CapabilityDetector.class);
    private final SystemDiskResolver systemDiskResolver = mock(SystemDiskResolver.class);
    private final DeviceDiscoveryService service =
            new DeviceDiscoveryService(gateway, capabilityDetector, systemDiskResolver);

    @Test
    void buildsAPhysicalDeviceFromDescriptorAndGeometry() {
        gateway.withDeviceDescriptor(
                0, new DeviceDescriptor(0, "ACME", "FastDrive 9000", "1.0", "SN123", 11, false));
        gateway.withGeometry(0, new DriveGeometry(0, 1000, 255, 63, 512, 500_000_000_000L));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());
        when(capabilityDetector.detectBusType(0)).thenReturn(BusType.SATA);
        when(capabilityDetector.detectMediaType(0, BusType.SATA)).thenReturn(MediaType.HDD);

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
        gateway.withDeviceDescriptor(0, new DeviceDescriptor(0, "ACME", "Model", "1.0", "SN0", 11, false));
        gateway.withGeometry(0, new DriveGeometry(0, 1, 1, 1, 512, 1024L));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of(0));
        when(capabilityDetector.detectBusType(0)).thenReturn(BusType.SATA);
        when(capabilityDetector.detectMediaType(0, BusType.SATA)).thenReturn(MediaType.HDD);

        PhysicalDevice device = service.discoverDevices().get(0);

        assertTrue(device.isSystemDisk());
    }

    @Test
    void skipsADiskThatFailsNativeQueriesRatherThanFailingTheWholeDiscovery() {
        gateway.withDeviceDescriptor(0, new DeviceDescriptor(0, "ACME", "GoodDrive", "1.0", "SN0", 11, false));
        gateway.withGeometry(0, new DriveGeometry(0, 1, 1, 1, 512, 1024L));
        // Disk 1 has a descriptor registered but no geometry -> queryGeometry(1) throws.
        gateway.withDeviceDescriptor(1, new DeviceDescriptor(1, "ACME", "BadDrive", "1.0", "SN1", 11, false));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());
        when(capabilityDetector.detectBusType(0)).thenReturn(BusType.SATA);
        when(capabilityDetector.detectMediaType(0, BusType.SATA)).thenReturn(MediaType.HDD);
        when(capabilityDetector.detectBusType(1)).thenReturn(BusType.SATA);
        when(capabilityDetector.detectMediaType(1, BusType.SATA)).thenReturn(MediaType.HDD);

        List<PhysicalDevice> devices = service.discoverDevices();

        assertEquals(1, devices.size());
        assertEquals("GoodDrive", devices.get(0).model());
    }

    @Test
    void discoverDeviceReturnsEmptyOptionalWhenTheDiskHasNoNativeData() {
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        Optional<PhysicalDevice> result = service.discoverDevice(99);

        assertTrue(result.isEmpty());
    }

    @Test
    void discoverDeviceReturnsThePhysicalDeviceWhenNativeDataIsAvailable() {
        gateway.withDeviceDescriptor(0, new DeviceDescriptor(0, "ACME", "Model", "1.0", "SN0", 11, true));
        gateway.withGeometry(0, new DriveGeometry(0, 1, 1, 1, 4096, 2048L));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());
        when(capabilityDetector.detectBusType(0)).thenReturn(BusType.USB);
        when(capabilityDetector.detectMediaType(0, BusType.USB)).thenReturn(MediaType.SSD);

        Optional<PhysicalDevice> result = service.discoverDevice(0);

        assertTrue(result.isPresent());
        assertEquals(BusType.USB, result.get().busType());
        assertTrue(result.get().isRemovable());
    }
}
