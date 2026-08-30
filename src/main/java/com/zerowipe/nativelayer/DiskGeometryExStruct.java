package com.zerowipe.nativelayer;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.util.List;

/**
 * The fixed-size {@code Geometry}+{@code DiskSize} header of
 * {@code DISK_GEOMETRY_EX}, the output of
 * {@code IOCTL_DISK_GET_DRIVE_GEOMETRY_EX}.
 *
 * <pre>
 * typedef struct _DISK_GEOMETRY {
 *   LARGE_INTEGER Cylinders;         // offset 0 (8 bytes)
 *   MEDIA_TYPE    MediaType;         // offset 8 (4-byte enum)
 *   DWORD         TracksPerCylinder; // offset 12
 *   DWORD         SectorsPerTrack;   // offset 16
 *   DWORD         BytesPerSector;    // offset 20
 * } DISK_GEOMETRY;                    // sizeof = 24
 *
 * typedef struct _DISK_GEOMETRY_EX {
 *   DISK_GEOMETRY Geometry;    // offset 0  (24 bytes)
 *   LARGE_INTEGER DiskSize;    // offset 24 (8 bytes; DISK_GEOMETRY is
 *                              // already a multiple of 8 bytes, so no
 *                              // padding is inserted before DiskSize)
 *   UCHAR         Data[1];     // offset 32 (flexible array - trailing
 *                              // partition information, not mapped)
 * } DISK_GEOMETRY_EX;
 * </pre>
 *
 * <p>Windows can return more data after the {@code Data[1]} marker than
 * {@code sizeof(DISK_GEOMETRY_EX)} suggests, so the caller allocates an
 * output buffer noticeably larger than 32 bytes and constructs this
 * structure directly from that buffer's {@link Pointer} - only the first
 * 32 bytes are ever read into Java fields.
 */
public final class DiskGeometryExStruct extends Structure {

    public long cylinders;
    public int mediaType;
    public int tracksPerCylinder;
    public int sectorsPerTrack;
    public int bytesPerSector;
    public long diskSize;

    DiskGeometryExStruct(Pointer buffer) {
        super(buffer);
        read();
    }

    @Override
    protected List<String> getFieldOrder() {
        return List.of(
                "cylinders", "mediaType", "tracksPerCylinder", "sectorsPerTrack", "bytesPerSector", "diskSize");
    }
}
