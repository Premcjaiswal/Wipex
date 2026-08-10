package com.zerowipe.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zerowipe.nativelayer.AdapterDescriptor;
import com.zerowipe.nativelayer.FakeNativeDeviceGateway;
import com.zerowipe.nativelayer.SeekPenaltyInfo;
import com.zerowipe.nativelayer.StorageBusTypeValues;
import com.zerowipe.nativelayer.TrimInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityDetectorTest {

    private final FakeNativeDeviceGateway gateway = new FakeNativeDeviceGateway();
    private final PowerShellQueryService powerShell = mock(PowerShellQueryService.class);
    private final CapabilityDetector detector = new CapabilityDetector(gateway, powerShell);

    @Test
    void mapsSataBusType() {
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.SATA));

        assertEquals(BusType.SATA, detector.detectBusType(0));
    }

    @Test
    void mapsNvmeBusType() {
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.NVME));

        assertEquals(BusType.NVME, detector.detectBusType(0));
    }

    @Test
    void mapsUsbBusType() {
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.USB));

        assertEquals(BusType.USB, detector.detectBusType(0));
    }

    @Test
    void mapsRaidBusType() {
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.RAID));

        assertEquals(BusType.RAID, detector.detectBusType(0));
    }

    @Test
    void mapsSasBusType() {
        gateway.withAdapterDescriptor(0, adapter(0, StorageBusTypeValues.SAS));

        assertEquals(BusType.SAS, detector.detectBusType(0));
    }

    @Test
    void unrecognisedBusTypeMapsToUnknown() {
        gateway.withAdapterDescriptor(0, adapter(0, 0x03)); // BusTypeAta - not one we distinguish

        assertEquals(BusType.UNKNOWN, detector.detectBusType(0));
    }

    @Test
    void nvmeMediaTypeIsDecidedFromBusTypeAloneWithoutQueryingSeekPenalty() {
        MediaType result = detector.detectMediaType(0, BusType.NVME);

        assertEquals(MediaType.NVME, result);
        // No fake seek-penalty was registered for disk 0; if the detector
        // had queried it, this would have thrown NativeAccessException.
    }

    @Test
    void seekPenaltyAndPowerShellAgreeingOnHddYieldsHdd() {
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, true));
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "HDD", "Healthy")));

        assertEquals(MediaType.HDD, detector.detectMediaType(0, BusType.SATA));
    }

    @Test
    void seekPenaltyAndPowerShellAgreeingOnSsdYieldsSsd() {
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, false));
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "SSD", "Healthy")));

        assertEquals(MediaType.SSD, detector.detectMediaType(0, BusType.SATA));
    }

    @Test
    void seekPenaltyImpliesHddButPowerShellReportsSsdYieldsUnknown() {
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, true));
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "SSD", "Healthy")));

        assertEquals(MediaType.UNKNOWN, detector.detectMediaType(0, BusType.SATA));
    }

    @Test
    void seekPenaltyImpliesSsdButPowerShellReportsHddYieldsUnknown() {
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, false));
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "HDD", "Healthy")));

        assertEquals(MediaType.UNKNOWN, detector.detectMediaType(0, BusType.SATA));
    }

    @Test
    void missingPowerShellDataFallsBackToSeekPenaltyAlone() {
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, true));
        when(powerShell.queryPhysicalDisks()).thenReturn(List.of()); // disk 0 not present

        assertEquals(MediaType.HDD, detector.detectMediaType(0, BusType.SATA));
    }

    @Test
    void unrecognisedPowerShellMediaTypeDisagreesAndYieldsUnknown() {
        gateway.withSeekPenalty(0, new SeekPenaltyInfo(0, false));
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "Unspecified", "Healthy")));

        assertEquals(MediaType.UNKNOWN, detector.detectMediaType(0, BusType.SATA));
    }

    @Test
    void detectCapabilitiesCombinesTrimHealthWearAndPowerOnHours() {
        gateway.withTrimInfo(0, new TrimInfo(0, true));
        when(powerShell.queryPhysicalDisks())
                .thenReturn(List.of(new PhysicalDiskInfo(0, "SSD", "Warning")));
        when(powerShell.queryReliabilityCounters())
                .thenReturn(List.of(new StorageReliabilityCounterInfo(0, 7, 1234L)));

        DeviceCapabilities capabilities = detector.detectCapabilities(0);

        assertEquals(true, capabilities.supportsTrim());
        assertEquals("Warning", capabilities.smartHealthStatus());
        assertEquals(7, capabilities.wearPercentage());
        assertEquals(1234L, capabilities.powerOnHours());
    }

    @Test
    void detectCapabilitiesReallocatedSectorCountIsAlwaysNull() {
        gateway.withTrimInfo(0, new TrimInfo(0, false));
        when(powerShell.queryPhysicalDisks()).thenReturn(List.of());
        when(powerShell.queryReliabilityCounters()).thenReturn(List.of());

        DeviceCapabilities capabilities = detector.detectCapabilities(0);

        assertNull(capabilities.reallocatedSectorCount());
    }

    @Test
    void detectCapabilitiesWithNoPowerShellDataLeavesHealthAndWearNull() {
        gateway.withTrimInfo(0, new TrimInfo(0, false));
        when(powerShell.queryPhysicalDisks()).thenReturn(List.of());
        when(powerShell.queryReliabilityCounters()).thenReturn(List.of());

        DeviceCapabilities capabilities = detector.detectCapabilities(0);

        assertNull(capabilities.smartHealthStatus());
        assertNull(capabilities.wearPercentage());
        assertNull(capabilities.powerOnHours());
    }

    private static AdapterDescriptor adapter(int diskNumber, int busTypeRaw) {
        return new AdapterDescriptor(diskNumber, busTypeRaw, 65536, 32, 0);
    }
}
