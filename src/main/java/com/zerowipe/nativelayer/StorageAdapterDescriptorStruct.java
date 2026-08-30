package com.zerowipe.nativelayer;

import com.sun.jna.Structure;
import java.util.List;

/**
 * {@code STORAGE_ADAPTER_DESCRIPTOR}, the output of
 * {@code IOCTL_STORAGE_QUERY_PROPERTY} / {@code StorageAdapterProperty}.
 *
 * <pre>
 * typedef struct _STORAGE_ADAPTER_DESCRIPTOR {
 *   ULONG   Version;                  // offset 0
 *   ULONG   Size;                     // offset 4
 *   ULONG   MaximumTransferLength;    // offset 8
 *   ULONG   MaximumPhysicalPages;     // offset 12
 *   ULONG   AlignmentMask;            // offset 16
 *   BOOLEAN AdapterUsesPio;           // offset 20
 *   BOOLEAN AdapterScansDown;         // offset 21
 *   BOOLEAN CommandQueueing;          // offset 22
 *   BOOLEAN AcceleratedTransfer;      // offset 23
 *   UCHAR   BusType;                  // offset 24 - NOTE: 1 byte here,
 *                                     // unlike STORAGE_DEVICE_DESCRIPTOR's
 *                                     // 4-byte BusType enum field.
 *   USHORT  BusMajorVersion;          // offset 26 (1 byte padding at 25
 *                                     // for 2-byte alignment)
 *   USHORT  BusMinorVersion;          // offset 28
 *   UCHAR   SrbType;                  // offset 30 - added in the Windows 8
 *                                     // SDK (NTDDI_VERSION >= NTDDI_WIN8);
 *                                     // present unconditionally on our
 *                                     // Windows 10/11-only target.
 *   UCHAR   AddressType;              // offset 31
 * } STORAGE_ADAPTER_DESCRIPTOR;        // sizeof = 32
 * </pre>
 */
public final class StorageAdapterDescriptorStruct extends Structure {

    public int version;
    public int size;
    public int maximumTransferLength;
    public int maximumPhysicalPages;
    public int alignmentMask;
    public byte adapterUsesPio;
    public byte adapterScansDown;
    public byte commandQueueing;
    public byte acceleratedTransfer;
    public byte busType;
    public short busMajorVersion;
    public short busMinorVersion;
    public byte srbType;
    public byte addressType;

    @Override
    protected List<String> getFieldOrder() {
        return List.of(
                "version",
                "size",
                "maximumTransferLength",
                "maximumPhysicalPages",
                "alignmentMask",
                "adapterUsesPio",
                "adapterScansDown",
                "commandQueueing",
                "acceleratedTransfer",
                "busType",
                "busMajorVersion",
                "busMinorVersion",
                "srbType",
                "addressType");
    }
}
