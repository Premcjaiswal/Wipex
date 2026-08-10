package com.zerowipe.nativelayer;

import com.sun.jna.Structure;
import java.util.List;

/**
 * {@code DEVICE_SEEK_PENALTY_DESCRIPTOR}, the output of
 * {@code IOCTL_STORAGE_QUERY_PROPERTY} / {@code StorageDeviceSeekPenaltyProperty}.
 *
 * <pre>
 * typedef struct _DEVICE_SEEK_PENALTY_DESCRIPTOR {
 *   DWORD   Version;             // offset 0
 *   DWORD   Size;                // offset 4
 *   BOOLEAN IncursSeekPenalty;   // offset 8
 * } DEVICE_SEEK_PENALTY_DESCRIPTOR;  // sizeof = 12 (padded to 4-byte alignment)
 * </pre>
 */
public final class DeviceSeekPenaltyDescriptorStruct extends Structure {

    public int version;
    public int size;
    public byte incursSeekPenalty;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("version", "size", "incursSeekPenalty");
    }
}
