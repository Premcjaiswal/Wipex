package com.zerowipe.nativelayer;

/**
 * Result of {@code IOCTL_STORAGE_QUERY_PROPERTY} /
 * {@code StorageDeviceSeekPenaltyProperty}. {@code incursSeekPenalty} true
 * means the device is a rotational HDD; false means it has no seek
 * penalty (SSD or NVMe).
 */
public record SeekPenaltyInfo(int diskNumber, boolean incursSeekPenalty) {
}
