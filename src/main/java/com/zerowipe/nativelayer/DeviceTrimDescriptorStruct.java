package com.zerowipe.nativelayer;

import com.sun.jna.Structure;
import java.util.List;

/**
 * {@code DEVICE_TRIM_DESCRIPTOR}, the output of
 * {@code IOCTL_STORAGE_QUERY_PROPERTY} / {@code StorageDeviceTrimProperty}.
 *
 * <pre>
 * typedef struct _DEVICE_TRIM_DESCRIPTOR {
 *   DWORD   Version;       // offset 0
 *   DWORD   Size;          // offset 4
 *   BOOLEAN TrimEnabled;   // offset 8
 * } DEVICE_TRIM_DESCRIPTOR;  // sizeof = 12 (padded to 4-byte alignment)
 * </pre>
 */
final class DeviceTrimDescriptorStruct extends Structure {

    public int version;
    public int size;
    public byte trimEnabled;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("version", "size", "trimEnabled");
    }
}
