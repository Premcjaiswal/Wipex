package com.zerowipe.nativelayer;

import java.util.List;

/**
 * The single boundary for all native Windows storage API access. Nothing
 * outside this package touches JNA - everything above this interface works
 * with plain Java types only, which is what makes the rest of the
 * application unit-testable with {@code FakeNativeDeviceGateway}.
 *
 * <p>This phase implements read-only operations only.
 * {@link #writeSectors(int, long, byte[])} is declared for interface
 * completeness (the future write-path phase) but throws
 * {@link UnsupportedOperationException} for now.
 *
 * <p>{@link #queryVolumeDiskExtents(String)} is not part of the original
 * interface sketch in the project's design notes; it was added in this
 * phase because system-disk detection (resolving the volume holding
 * {@code %SystemRoot%} to a physical disk number via
 * {@code IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS}) needs a native call that
 * has to live behind this same boundary.
 */
public interface NativeDeviceGateway {

    /** Probes {@code \\.\PhysicalDrive0} through {@code \\.\PhysicalDrive15} and returns the ones that open. */
    List<Integer> enumerateDiskNumbers();

    DeviceDescriptor queryDeviceProperty(int diskNumber);

    SeekPenaltyInfo querySeekPenalty(int diskNumber);

    TrimInfo queryTrimSupport(int diskNumber);

    AdapterDescriptor queryAdapter(int diskNumber);

    DriveGeometry queryGeometry(int diskNumber);

    /**
     * Returns the physical disk numbers a volume spans (normally one),
     * resolved via {@code IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS}.
     *
     * @param volumePath a drive-letter volume identifier such as {@code "C:"}
     */
    List<Integer> queryVolumeDiskExtents(String volumePath);

    /** Not implemented in this phase. */
    void writeSectors(int diskNumber, long offset, byte[] buffer);

    /**
     * Reads {@code length} bytes starting at {@code offset}. Both must be
     * exact multiples of the device's sector size, or the call fails -
     * unaligned raw disk reads are rejected rather than silently rounded.
     */
    byte[] readSectors(int diskNumber, long offset, int length);
}
