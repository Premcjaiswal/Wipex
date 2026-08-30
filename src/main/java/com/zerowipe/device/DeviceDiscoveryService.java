package com.zerowipe.device;

import com.zerowipe.nativelayer.AdapterDescriptor;
import com.zerowipe.nativelayer.DeviceDescriptor;
import com.zerowipe.nativelayer.DriveGeometry;
import com.zerowipe.nativelayer.NativeAccessException;
import com.zerowipe.nativelayer.NativeDeviceGateway;
import com.zerowipe.nativelayer.StorageBusTypeValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Enumerates physical drives and identifies them as {@link PhysicalDevice}
 * records, combining raw identity data from {@link NativeDeviceGateway}
 * with system-disk status from {@link SystemDiskResolver}.
 *
 * <p>Bus/media type detection (seek-penalty vs {@code Get-PhysicalDisk}
 * cross-check) is inlined here for now rather than living in a separate
 * classifier - it's slated to simplify further to PowerShell-only detection
 * next, so a short-lived intermediate collaborator class isn't worth
 * introducing.
 *
 * <p>A disk that fails to answer any of the required native queries is
 * skipped (logged, not thrown) rather than failing the whole discovery
 * pass - one uncooperative or transient drive should not hide every other
 * device on the report.
 */
@Service
public class DeviceDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceDiscoveryService.class);

    private final NativeDeviceGateway nativeDeviceGateway;
    private final PowerShellQueryService powerShellQueryService;
    private final SystemDiskResolver systemDiskResolver;

    public DeviceDiscoveryService(
            NativeDeviceGateway nativeDeviceGateway,
            PowerShellQueryService powerShellQueryService,
            SystemDiskResolver systemDiskResolver) {
        this.nativeDeviceGateway = nativeDeviceGateway;
        this.powerShellQueryService = powerShellQueryService;
        this.systemDiskResolver = systemDiskResolver;
    }

    public List<PhysicalDevice> discoverDevices() {
        Set<Integer> systemDiskNumbers = systemDiskResolver.systemDiskNumbers();
        List<PhysicalDevice> devices = new ArrayList<>();
        for (int diskNumber : nativeDeviceGateway.enumerateDiskNumbers()) {
            try {
                devices.add(describeDevice(diskNumber, systemDiskNumbers));
            } catch (NativeAccessException e) {
                log.warn("Skipping disk {} during discovery: {}", diskNumber, e.getMessage());
            }
        }
        return List.copyOf(devices);
    }

    public Optional<PhysicalDevice> discoverDevice(int diskNumber) {
        try {
            return Optional.of(describeDevice(diskNumber, systemDiskResolver.systemDiskNumbers()));
        } catch (NativeAccessException e) {
            log.warn("Could not describe disk {}: {}", diskNumber, e.getMessage());
            return Optional.empty();
        }
    }

    private PhysicalDevice describeDevice(int diskNumber, Set<Integer> systemDiskNumbers) {
        DeviceDescriptor descriptor = nativeDeviceGateway.queryDeviceProperty(diskNumber);
        DriveGeometry geometry = nativeDeviceGateway.queryGeometry(diskNumber);
        BusType busType = detectBusType(diskNumber);
        MediaType mediaType = detectMediaType(diskNumber, busType);

        return new PhysicalDevice(
                diskNumber,
                descriptor.model(),
                descriptor.serialNumber(),
                descriptor.firmwareRevision(),
                busType,
                mediaType,
                geometry.totalCapacityBytes(),
                geometry.bytesPerSector(),
                systemDiskNumbers.contains(diskNumber),
                descriptor.removableMedia());
    }

    private BusType detectBusType(int diskNumber) {
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
    private MediaType detectMediaType(int diskNumber, BusType busType) {
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

    private Optional<PhysicalDiskInfo> findPhysicalDisk(int diskNumber) {
        List<PhysicalDiskInfo> disks = powerShellQueryService.queryPhysicalDisks();
        return disks.stream().filter(d -> diskNumber == intOrMinusOne(d.deviceId())).findFirst();
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
