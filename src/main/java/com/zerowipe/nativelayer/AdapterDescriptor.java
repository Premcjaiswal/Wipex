package com.zerowipe.nativelayer;

/**
 * Result of {@code IOCTL_STORAGE_QUERY_PROPERTY} / {@code StorageAdapterProperty}.
 * {@code busTypeRaw} is the numeric {@code STORAGE_BUS_TYPE} value; callers
 * map it to the domain {@code BusType} enum.
 */
public record AdapterDescriptor(
        int diskNumber,
        int busTypeRaw,
        int maximumTransferLength,
        int maximumPhysicalPages,
        int alignmentMask) {
}
