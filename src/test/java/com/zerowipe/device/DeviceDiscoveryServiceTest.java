package com.zerowipe.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zerowipe.nativelayer.AdapterDescriptor;
import com.zerowipe.nativelayer.DeviceDescriptor;
import com.zerowipe.nativelayer.DriveGeometry;
import com.zerowipe.nativelayer.FakeNativeDeviceGateway;
import com.zerowipe.nativelayer.SeekPenaltyInfo;
import com.zerowipe.nativelayer.StorageBusTypeValues;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeviceDiscoveryServiceTest {

    private final FakeNativeDeviceGateway gateway = new FakeNativeDeviceGateway();
    private final PowerShellQueryService powerShell = mock(PowerShellQueryService.class);
    private final SystemDiskResolver systemDiskResolver = mock(SystemDiskResolver.class);
    private final DeviceDiscoveryService service = new DeviceDiscoveryService(gateway, powerShell, systemDiskResolver);

    @Test
    void buildsAPhysicalDeviceFromDescriptorAndGeometry() {
        gateway.withDeviceDescriptor(
                0, new DeviceDescriptor(0, "ACME", "FastDrive 9000", "1.0", "SN123", 11, false));
        gateway.withGeometry(0, new DriveGeometry(0, 1000, 255, 63, 512, 500_000_000_000L));
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.SATA));
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, true));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());
        when(powerShell.queryPhysicalDisks()).thenReturn(List.of());

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
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.SATA));
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, true));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of(0));
        when(powerShell.queryPhysicalDisks()).thenReturn(List.of());

        PhysicalDevice device = service.discoverDevices().get(0);

        assertTrue(device.isSystemDisk());
    }

    @Test
    void skipsADiskThatFailsNativeQueriesRatherThanFailingTheWholeDiscovery() {
        gateway.withDeviceDescriptor(0, new DeviceDescriptor(0, "ACME", "GoodDrive", "1.0", "SN0", 11, false));
        gateway.withGeometry(0, new DriveGeometry(0, 1, 1, 1, 512, 1024L));
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.SATA));
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, true));
        // Disk 1 has a descriptor registered but no geometry -> queryGeometry(1) throws.
        gateway.withDeviceDescriptor(1, new DeviceDescriptor(1, "ACME", "BadDrive", "1.0", "SN1", 11, false));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());
        when(powerShell.queryPhysicalDisks()).thenReturn(List.of());

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
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.USB));
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, false));
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());
        when(powerShell.queryPhysicalDisks()).thenReturn(List.of());

        Optional<PhysicalDevice> result = service.discoverDevice(0);

        assertTrue(result.isPresent());
        assertEquals(BusType.USB, result.get().busType());
        assertEquals(MediaType.SSD, result.get().mediaType());
        assertTrue(result.get().isRemovable());
    }

    @Test
    void nvmeBusTypeSkipsTheSeekPenaltyCrossCheckEntirely() {
        gateway.withDeviceDescriptor(0, new DeviceDescriptor(0, "ACME", "Model", "1.0", "SN0", 17, false));
        gateway.withGeometry(0, new DriveGeometry(0, 1, 1, 1, 512, 1024L));
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.NVME));
        // No fake seek-penalty registered for disk 0: if detectMediaType had
        // queried it, FakeNativeDeviceGateway would throw NativeAccessException.
        when(systemDiskResolver.systemDiskNumbers()).thenReturn(Set.of());

        PhysicalDevice device = service.discoverDevices().get(0);

        assertEquals(BusType.NVME, device.busType());
        assertEquals(MediaType.NVME, device.mediaType());
    }

    private static AdapterDescriptor adapter(int diskNumber, int busTypeRaw) {
        return new AdapterDescriptor(diskNumber, busTypeRaw, 65536, 32, 0);
    }
}
