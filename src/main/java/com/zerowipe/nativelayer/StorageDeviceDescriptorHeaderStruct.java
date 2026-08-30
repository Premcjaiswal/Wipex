package com.zerowipe.nativelayer;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.util.List;

/**
 * The fixed-size header of {@code STORAGE_DEVICE_DESCRIPTOR}, the output
 * of {@code IOCTL_STORAGE_QUERY_PROPERTY} / {@code StorageDeviceProperty}.
 *
 * <pre>
 * typedef struct _STORAGE_DEVICE_DESCRIPTOR {
 *   ULONG  Version;                  // offset 0
 *   ULONG  Size;                     // offset 4
 *   UCHAR  DeviceType;               // offset 8
 *   UCHAR  DeviceTypeModifier;       // offset 9
 *   BOOLEAN RemovableMedia;          // offset 10
 *   BOOLEAN CommandQueueing;         // offset 11
 *   ULONG  VendorIdOffset;           // offset 12
 *   ULONG  ProductIdOffset;          // offset 16
 *   ULONG  ProductRevisionOffset;    // offset 20
 *   ULONG  SerialNumberOffset;       // offset 24
 *   STORAGE_BUS_TYPE BusType;        // offset 28 (4-byte enum)
 *   ULONG  RawPropertiesLength;      // offset 32
 *   UCHAR  RawDeviceProperties[1];   // offset 36 (flexible array, not mapped)
 * } STORAGE_DEVICE_DESCRIPTOR;
 * </pre>
 *
 * <p>The four single-byte fields (DeviceType/DeviceTypeModifier/RemovableMedia/
 * CommandQueueing) sum to exactly 4 bytes, so no padding is needed before
 * the following ULONG fields - natural alignment already keeps everything
 * DWORD-aligned.
 *
 * <p>The four *Offset fields are byte offsets into this SAME output
 * buffer, not inline strings - the caller must read the offset, then read
 * a null-terminated ASCII string starting at that absolute position in the
 * buffer this structure was constructed from. This structure is
 * constructed directly from the output buffer's {@link Pointer} (rather
 * than allocating its own memory) specifically so those offsets can be
 * resolved against the same buffer afterward.
 */
public final class StorageDeviceDescriptorHeaderStruct extends Structure {

    public int version;
    public int size;
    public byte deviceType;
    public byte deviceTypeModifier;
    public byte removableMedia;
    public byte commandQueueing;
    public int vendorIdOffset;
    public int productIdOffset;
    public int productRevisionOffset;
    public int serialNumberOffset;
    public int busType;
    public int rawPropertiesLength;

    StorageDeviceDescriptorHeaderStruct(Pointer buffer) {
        super(buffer);
        read();
    }

    @Override
    protected List<String> getFieldOrder() {
        return List.of(
                "version",
                "size",
                "deviceType",
                "deviceTypeModifier",
                "removableMedia",
                "commandQueueing",
                "vendorIdOffset",
                "productIdOffset",
                "productRevisionOffset",
                "serialNumberOffset",
                "busType",
                "rawPropertiesLength");
    }
}
