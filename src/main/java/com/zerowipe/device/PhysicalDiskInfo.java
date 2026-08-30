package com.zerowipe.device;

/**
 * Parsed row from {@code Get-PhysicalDisk | Select-Object DeviceId,
 * FriendlyName, SerialNumber, FirmwareVersion, BusType, MediaType, Size,
 * LogicalSectorSize | ConvertTo-Json}. {@code deviceId} corresponds to the
 * {@code \\.\PhysicalDriveN} disk number - this is the sole source of
 * device identity now that native IOCTL detection has been removed.
 */
record PhysicalDiskInfo(
        Integer deviceId,
        String friendlyName,
        String serialNumber,
        String firmwareVersion,
        String busType,
        String mediaType,
        Long size,
        Integer logicalSectorSize) {
}
