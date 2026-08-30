package com.zerowipe.nativelayer;

/**
 * Result of {@code IOCTL_DISK_GET_DRIVE_GEOMETRY_EX}. {@code totalCapacityBytes}
 * comes from the structure's {@code DiskSize} field, which is the accurate
 * total capacity - it is not recomputed from cylinders/tracks/sectors.
 */
public record DriveGeometry(
        int diskNumber,
        long cylinders,
        int tracksPerCylinder,
        int sectorsPerTrack,
        int bytesPerSector,
        long totalCapacityBytes) {
}
