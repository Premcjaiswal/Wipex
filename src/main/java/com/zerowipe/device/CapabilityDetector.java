package com.zerowipe.device;

import com.zerowipe.nativelayer.AdapterDescriptor;
import com.zerowipe.nativelayer.NativeDeviceGateway;
import com.zerowipe.nativelayer.StorageBusTypeValues;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Determines what a device actually is: bus type, media type (with a
 * seek-penalty vs {@code Get-PhysicalDisk} cross-check), TRIM support, and
 * SMART-derived health data. This is the one place bus/media
 * classification happens - {@link DeviceDiscoveryService} calls into it
 * to build each {@code PhysicalDevice}, and the {@code /capabilities} API
 * calls it directly for {@code DeviceCapabilities}.
 */
@Service
public class CapabilityDetector {

    private static final Logger log = LoggerFactory.getLogger(CapabilityDetector.class);

    private final NativeDeviceGateway nativeDeviceGateway;
    private final PowerShellQueryService powerShellQueryService;

    public CapabilityDetector(NativeDeviceGateway nativeDeviceGateway, PowerShellQueryService powerShellQueryService) {
        this.nativeDeviceGateway = nativeDeviceGateway;
        this.powerShellQueryService = powerShellQueryService;
    }

    public BusType detectBusType(int diskNumber) {
        AdapterDescriptor adapter = nativeDeviceGateway.queryAdapter(diskNumber);
        return mapBusType(adapter.busTypeRaw());
    }

    /**
     * Cross-checks the seek-penalty heuristic (HDD vs SSD) against {@code
     * Get-PhysicalDisk}'s reported media type. NVMe is decided purely from
     * the bus type, since NVMe devices never report a seek penalty and
     * {@code Get-PhysicalDisk} reports them as plain "SSD" - there is
     * nothing to disagree about. When the two HDD/SSD sources disagree, or
     * PowerShell data is missing for the disk, the outcome is recorded via
     * SLF4J and, on disagreement, the media type is set to UNKNOWN.
     */
    public MediaType detectMediaType(int diskNumber, BusType busType) {
        if (busType == BusType.NVME) {
            return MediaType.NVME;
        }

        boolean incursSeekPenalty = nativeDeviceGateway.querySeekPenalty(diskNumber).incursSeekPenalty();
        MediaType seekPenaltyImplied = incursSeekPenalty ? MediaType.HDD : MediaType.SSD;

        Optional<String> reportedMediaType = findPhysicalDisk(diskNumber).map(PhysicalDiskInfo::mediaType);
        if (reportedMediaType.isEmpty()) {
            log.info(
                    "No Get-PhysicalDisk data for disk {}; using seek-penalty result {} without cross-check",
                    diskNumber,
                    seekPenaltyImplied);
            return seekPenaltyImplied;
        }

        MediaType powerShellImplied = mapPowerShellMediaType(reportedMediaType.get());
        if (powerShellImplied == seekPenaltyImplied) {
            return seekPenaltyImplied;
        }

        log.warn(
                "Media type cross-check disagreement for disk {}: seek penalty implies {}, Get-PhysicalDisk "
                        + "reports '{}' (mapped to {}); marking media type UNKNOWN",
                diskNumber,
                seekPenaltyImplied,
                reportedMediaType.get(),
                powerShellImplied);
        return MediaType.UNKNOWN;
    }

    public DeviceCapabilities detectCapabilities(int diskNumber) {
        boolean trimEnabled = nativeDeviceGateway.queryTrimSupport(diskNumber).trimEnabled();

        Optional<PhysicalDiskInfo> physicalDisk = findPhysicalDisk(diskNumber);
        Optional<StorageReliabilityCounterInfo> reliability = findReliabilityCounter(diskNumber);

        String healthStatus = physicalDisk.map(PhysicalDiskInfo::healthStatus).orElse(null);
        Integer wearPercentage = reliability.map(StorageReliabilityCounterInfo::wear).orElse(null);
        Long powerOnHours = reliability.map(StorageReliabilityCounterInfo::powerOnHours).orElse(null);

        // Reallocated sector count is not populated - see StorageReliabilityCounterInfo's javadoc.
        return new DeviceCapabilities(trimEnabled, null, healthStatus, wearPercentage, powerOnHours);
    }

    private Optional<PhysicalDiskInfo> findPhysicalDisk(int diskNumber) {
        List<PhysicalDiskInfo> disks = powerShellQueryService.queryPhysicalDisks();
        return disks.stream().filter(d -> diskNumber == intOrMinusOne(d.deviceId())).findFirst();
    }

    private Optional<StorageReliabilityCounterInfo> findReliabilityCounter(int diskNumber) {
        List<StorageReliabilityCounterInfo> counters = powerShellQueryService.queryReliabilityCounters();
        return counters.stream().filter(c -> diskNumber == intOrMinusOne(c.deviceId())).findFirst();
    }

    private static int intOrMinusOne(Integer value) {
        return value == null ? -1 : value;
    }

    private static MediaType mapPowerShellMediaType(String raw) {
        if (raw == null) {
            return MediaType.UNKNOWN;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "HDD" -> MediaType.HDD;
            case "SSD" -> MediaType.SSD;
            default -> MediaType.UNKNOWN; // e.g. "SCM", "Unspecified"
        };
    }

    /**
     * Maps the raw {@code STORAGE_BUS_TYPE} value from {@code
     * STORAGE_ADAPTER_DESCRIPTOR}. Only the values this project's
     * {@link BusType} enum distinguishes are mapped; every other bus type
     * (SCSI, ATA, 1394, Fibre Channel, iSCSI, Storage Spaces, etc.) maps to
     * UNKNOWN.
     */
    private static BusType mapBusType(int busTypeRaw) {
        return switch (busTypeRaw) {
            case StorageBusTypeValues.SATA -> BusType.SATA;
            case StorageBusTypeValues.NVME -> BusType.NVME;
            case StorageBusTypeValues.USB -> BusType.USB;
            case StorageBusTypeValues.RAID -> BusType.RAID;
            case StorageBusTypeValues.SAS -> BusType.SAS;
            default -> BusType.UNKNOWN;
        };
    }
}
