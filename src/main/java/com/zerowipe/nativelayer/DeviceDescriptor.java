package com.zerowipe.nativelayer;

/**
 * Raw identity fields read from {@code STORAGE_DEVICE_DESCRIPTOR} via
 * {@code IOCTL_STORAGE_QUERY_PROPERTY} / {@code StorageDeviceProperty}.
 * {@code busTypeRaw} is the numeric {@code STORAGE_BUS_TYPE} value as
 * reported by the device descriptor itself; callers map it to the domain
 * {@code BusType} enum.
 */
public record DeviceDescriptor(
        int diskNumber,
        String vendorId,
        String model,
        String firmwareRevision,
        String serialNumber,
        int busTypeRaw,
        boolean removableMedia) {
}
