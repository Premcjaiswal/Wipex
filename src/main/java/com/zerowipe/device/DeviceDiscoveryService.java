package com.zerowipe.device;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Builds {@link PhysicalDevice} records entirely from {@code
 * Get-PhysicalDisk} - one row per disk, so no separate "enumerate" call is
 * needed - combined with system-disk status from {@link SystemDiskResolver}.
 *
 * <p>{@code isRemovable} is approximated as "bus type is USB" -
 * {@code Get-PhysicalDisk} doesn't expose a direct removable-media flag the
 * way the old native {@code STORAGE_DEVICE_DESCRIPTOR} did. Worth
 * double-checking against real hardware with a non-USB removable drive if
 * one is available; for the common case (internal SATA/NVMe vs. USB
 * stick) this holds.
 */
@Service
public class DeviceDiscoveryService {

    private final PowerShellQueryService powerShellQueryService;
    private final SystemDiskResolver systemDiskResolver;

    public DeviceDiscoveryService(
            PowerShellQueryService powerShellQueryService, SystemDiskResolver systemDiskResolver) {
        this.powerShellQueryService = powerShellQueryService;
        this.systemDiskResolver = systemDiskResolver;
    }

    public List<PhysicalDevice> discoverDevices() {
        Set<Integer> systemDiskNumbers = systemDiskResolver.systemDiskNumbers();
        return powerShellQueryService.queryPhysicalDisks().stream()
                .filter(disk -> disk.deviceId() != null)
                .map(disk -> toPhysicalDevice(disk, systemDiskNumbers))
                .toList();
    }

    public Optional<PhysicalDevice> discoverDevice(int diskNumber) {
        Set<Integer> systemDiskNumbers = systemDiskResolver.systemDiskNumbers();
        return powerShellQueryService.queryPhysicalDisks().stream()
                .filter(disk -> diskNumber == intOrMinusOne(disk.deviceId()))
                .findFirst()
                .map(disk -> toPhysicalDevice(disk, systemDiskNumbers));
    }

    private static PhysicalDevice toPhysicalDevice(PhysicalDiskInfo disk, Set<Integer> systemDiskNumbers) {
        int diskNumber = disk.deviceId();
        BusType busType = mapBusType(disk.busType());
        MediaType mediaType = resolveMediaType(busType, disk.mediaType());

        return new PhysicalDevice(
                diskNumber,
                disk.friendlyName(),
                disk.serialNumber(),
                disk.firmwareVersion(),
                busType,
                mediaType,
                disk.size() == null ? 0L : disk.size(),
                disk.logicalSectorSize() == null ? 0 : disk.logicalSectorSize(),
                systemDiskNumbers.contains(diskNumber),
                busType == BusType.USB);
    }

    private static int intOrMinusOne(Integer value) {
        return value == null ? -1 : value;
    }

    /**
     * NVMe is decided from bus type, not {@code Get-PhysicalDisk}'s
     * MediaType field - Microsoft's storage model reports NVMe drives'
     * MediaType as plain "SSD" (or "Unspecified"), since NVMe is a bus, not
     * a media type.
     */
    private static MediaType resolveMediaType(BusType busType, String rawMediaType) {
        if (busType == BusType.NVME) {
            return MediaType.NVME;
        }
        if (rawMediaType == null) {
            return MediaType.UNKNOWN;
        }
        return switch (rawMediaType.trim().toUpperCase(Locale.ROOT)) {
            case "HDD" -> MediaType.HDD;
            case "SSD" -> MediaType.SSD;
            default -> MediaType.UNKNOWN; // e.g. "SCM", "Unspecified"
        };
    }

    private static BusType mapBusType(String rawBusType) {
        if (rawBusType == null) {
            return BusType.UNKNOWN;
        }
        return switch (rawBusType.trim().toUpperCase(Locale.ROOT)) {
            case "SATA" -> BusType.SATA;
            case "NVME" -> BusType.NVME;
            case "USB" -> BusType.USB;
            case "RAID" -> BusType.RAID;
            case "SAS" -> BusType.SAS;
            default -> BusType.UNKNOWN;
        };
    }
}
