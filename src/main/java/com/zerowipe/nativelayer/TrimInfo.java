package com.zerowipe.nativelayer;

/**
 * Result of {@code IOCTL_STORAGE_QUERY_PROPERTY} / {@code StorageDeviceTrimProperty}.
 */
public record TrimInfo(int diskNumber, boolean trimEnabled) {
}
