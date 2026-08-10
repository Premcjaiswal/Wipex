package com.zerowipe.nativelayer;

import java.util.List;

/**
 * Placeholder for the future write-path phase. {@code volumePaths} names
 * the volumes locked and dismounted for a disk; unused until
 * {@code lockAndDismountVolumes} is implemented.
 */
public record VolumeLockHandles(int diskNumber, List<String> volumePaths) {
}
