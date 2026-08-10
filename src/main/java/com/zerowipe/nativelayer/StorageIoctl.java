package com.zerowipe.nativelayer;

/**
 * IOCTL codes and related constants from {@code winioctl.h}, computed via
 * the standard {@code CTL_CODE} macro:
 * {@code (DeviceType << 16) | (Access << 14) | (Function << 2) | Method}.
 *
 * <p>Values are cross-checked against their well-known published hex
 * constants (widely cited in Windows storage interop code), not derived
 * from the macro alone.
 */
final class StorageIoctl {

    private StorageIoctl() {
    }

    // CTL_CODE(FILE_DEVICE_MASS_STORAGE=0x2d, 0x0500, METHOD_BUFFERED, FILE_ANY_ACCESS) = 0x2D1400
    static final int IOCTL_STORAGE_QUERY_PROPERTY = 0x002D1400;

    // CTL_CODE(FILE_DEVICE_DISK=0x07, 0x0028, METHOD_BUFFERED, FILE_ANY_ACCESS) = 0x700A0
    static final int IOCTL_DISK_GET_DRIVE_GEOMETRY_EX = 0x000700A0;

    // CTL_CODE(IOCTL_VOLUME_BASE='V'=0x56, 0, METHOD_BUFFERED, FILE_ANY_ACCESS) = 0x560000
    static final int IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS = 0x00560000;

    // STORAGE_PROPERTY_ID (subset used here)
    static final int PROPERTY_ID_STORAGE_DEVICE = 0;
    static final int PROPERTY_ID_STORAGE_ADAPTER = 1;
    static final int PROPERTY_ID_STORAGE_DEVICE_SEEK_PENALTY = 7;
    static final int PROPERTY_ID_STORAGE_DEVICE_TRIM = 8;

    // STORAGE_QUERY_TYPE
    static final int QUERY_TYPE_PROPERTY_STANDARD_QUERY = 0;

    // STORAGE_BUS_TYPE values live in the public StorageBusTypeValues class,
    // since the device layer needs them too.
}
