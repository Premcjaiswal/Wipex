package com.zerowipe.device;

/**
 * Identity and static properties of a physical storage device, as reported
 * by the Windows storage stack. Contains no capability or health data - see
 * {@link DeviceCapabilities} for that.
 */
public record PhysicalDevice(
        int diskNumber,
        String model,
        String serialNumber,
        String firmwareRevision,
        BusType busType,
        MediaType mediaType,
        long capacityBytes,
        int bytesPerSector,
        boolean isSystemDisk,
        boolean isRemovable) {
}
