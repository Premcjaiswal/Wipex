package com.zerowipe.device;

/**
 * Parsed row from {@code Get-PhysicalDisk | Select-Object DeviceId,
 * MediaType, HealthStatus | ConvertTo-Json}. {@code deviceId} corresponds
 * to the {@code \\.\PhysicalDriveN} disk number.
 */
record PhysicalDiskInfo(Integer deviceId, String mediaType, String healthStatus) {
}
