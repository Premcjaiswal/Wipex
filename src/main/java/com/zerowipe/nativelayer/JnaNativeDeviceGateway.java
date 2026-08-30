package com.zerowipe.nativelayer;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real {@link NativeDeviceGateway} implementation using JNA to call the
 * Windows storage IOCTLs directly. Read-only in this phase:
 * {@link #writeSectors(int, long, byte[])} throws
 * {@link UnsupportedOperationException}.
 *
 * <p>Every handle opened here is closed in a {@code finally} block, and
 * every failed Win32 call is reported with its {@code GetLastError()} code
 * via {@link NativeAccessException} rather than failing silently.
 */
@Component
public class JnaNativeDeviceGateway implements NativeDeviceGateway {

    private static final Logger log = LoggerFactory.getLogger(JnaNativeDeviceGateway.class);

    private static final int MIN_DISK_NUMBER = 0;
    private static final int MAX_DISK_NUMBER = 15;

    @Override
    public List<Integer> enumerateDiskNumbers() {
        List<Integer> found = new ArrayList<>();
        for (int diskNumber = MIN_DISK_NUMBER; diskNumber <= MAX_DISK_NUMBER; diskNumber++) {
            String path = physicalDrivePath(diskNumber);
            WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(
                    path,
                    WinNT.GENERIC_READ,
                    WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                    null,
                    WinNT.OPEN_EXISTING,
                    0,
                    null);
            if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
                int error = Kernel32.INSTANCE.GetLastError();
                log.debug("{} did not open (Win32 error {})", path, error);
                continue;
            }
            try {
                found.add(diskNumber);
            } finally {
                Kernel32.INSTANCE.CloseHandle(handle);
            }
        }
        return List.copyOf(found);
    }

    @Override
    public DeviceDescriptor queryDeviceProperty(int diskNumber) {
        WinNT.HANDLE handle = openPhysicalDrive(diskNumber);
        try {
            Memory output = new Memory(1024);
            output.clear();
            issueStoragePropertyQuery(
                    handle, diskNumber, StorageIoctl.PROPERTY_ID_STORAGE_DEVICE, output, (int) output.size());

            StorageDeviceDescriptorHeaderStruct header = new StorageDeviceDescriptorHeaderStruct(output);
            String vendorId = readOffsetString(output, header.vendorIdOffset);
            String model = readOffsetString(output, header.productIdOffset);
            String firmwareRevision = readOffsetString(output, header.productRevisionOffset);
            String serialNumber = readOffsetString(output, header.serialNumberOffset);
            boolean removable = header.removableMedia != 0;

            return new DeviceDescriptor(
                    diskNumber, vendorId, model, firmwareRevision, serialNumber, header.busType, removable);
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    @Override
    public SeekPenaltyInfo querySeekPenalty(int diskNumber) {
        WinNT.HANDLE handle = openPhysicalDrive(diskNumber);
        try {
            DeviceSeekPenaltyDescriptorStruct descriptor = new DeviceSeekPenaltyDescriptorStruct();
            issueStoragePropertyQuery(
                    handle,
                    diskNumber,
                    StorageIoctl.PROPERTY_ID_STORAGE_DEVICE_SEEK_PENALTY,
                    descriptor.getPointer(),
                    descriptor.size());
            descriptor.read();
            return new SeekPenaltyInfo(diskNumber, descriptor.incursSeekPenalty != 0);
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    @Override
    public TrimInfo queryTrimSupport(int diskNumber) {
        WinNT.HANDLE handle = openPhysicalDrive(diskNumber);
        try {
            DeviceTrimDescriptorStruct descriptor = new DeviceTrimDescriptorStruct();
            issueStoragePropertyQuery(
                    handle,
                    diskNumber,
                    StorageIoctl.PROPERTY_ID_STORAGE_DEVICE_TRIM,
                    descriptor.getPointer(),
                    descriptor.size());
            descriptor.read();
            return new TrimInfo(diskNumber, descriptor.trimEnabled != 0);
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    @Override
    public AdapterDescriptor queryAdapter(int diskNumber) {
        WinNT.HANDLE handle = openPhysicalDrive(diskNumber);
        try {
            StorageAdapterDescriptorStruct descriptor = new StorageAdapterDescriptorStruct();
            issueStoragePropertyQuery(
                    handle,
                    diskNumber,
                    StorageIoctl.PROPERTY_ID_STORAGE_ADAPTER,
                    descriptor.getPointer(),
                    descriptor.size());
            descriptor.read();
            return new AdapterDescriptor(
                    diskNumber,
                    descriptor.busType & 0xFF,
                    descriptor.maximumTransferLength,
                    descriptor.maximumPhysicalPages,
                    descriptor.alignmentMask);
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    @Override
    public DriveGeometry queryGeometry(int diskNumber) {
        WinNT.HANDLE handle = openPhysicalDrive(diskNumber);
        try {
            // DISK_GEOMETRY_EX can carry trailing partition data beyond
            // sizeof(DISK_GEOMETRY_EX)=32; allocate generously and only map
            // the fixed Geometry+DiskSize header.
            Memory output = new Memory(256);
            output.clear();
            IntByReference bytesReturned = new IntByReference();
            boolean ok = Kernel32.INSTANCE.DeviceIoControl(
                    handle,
                    StorageIoctl.IOCTL_DISK_GET_DRIVE_GEOMETRY_EX,
                    null,
                    0,
                    output,
                    (int) output.size(),
                    bytesReturned,
                    null);
            if (!ok) {
                int error = Kernel32.INSTANCE.GetLastError();
                throw new NativeAccessException(
                        "IOCTL_DISK_GET_DRIVE_GEOMETRY_EX failed for disk " + diskNumber, error);
            }
            DiskGeometryExStruct geometry = new DiskGeometryExStruct(output);
            return new DriveGeometry(
                    diskNumber,
                    geometry.cylinders,
                    geometry.tracksPerCylinder,
                    geometry.sectorsPerTrack,
                    geometry.bytesPerSector,
                    geometry.diskSize);
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    @Override
    public List<Integer> queryVolumeDiskExtents(String volumePath) {
        WinNT.HANDLE handle = openVolume(volumePath);
        try {
            Memory output = new Memory(1024);
            output.clear();
            IntByReference bytesReturned = new IntByReference();
            boolean ok = Kernel32.INSTANCE.DeviceIoControl(
                    handle,
                    StorageIoctl.IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS,
                    null,
                    0,
                    output,
                    (int) output.size(),
                    bytesReturned,
                    null);
            if (!ok) {
                int error = Kernel32.INSTANCE.GetLastError();
                throw new NativeAccessException(
                        "IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS failed for volume " + volumePath, error);
            }
            // VOLUME_DISK_EXTENTS: NumberOfDiskExtents (4 bytes) followed by
            // 4 bytes of padding (DISK_EXTENT needs 8-byte alignment because
            // of its LARGE_INTEGER members), then DISK_EXTENT[] starting at
            // offset 8. Each DISK_EXTENT is 24 bytes with DiskNumber as its
            // first (4-byte) field.
            int extentCount = output.getInt(0);
            List<Integer> diskNumbers = new ArrayList<>(extentCount);
            for (int i = 0; i < extentCount; i++) {
                long extentOffset = 8L + (long) i * 24L;
                diskNumbers.add(output.getInt(extentOffset));
            }
            return List.copyOf(diskNumbers);
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    @Override
    public void writeSectors(int diskNumber, long offset, byte[] buffer) {
        throw new UnsupportedOperationException("writeSectors is not implemented in this read-only phase");
    }

    @Override
    public byte[] readSectors(int diskNumber, long offset, int length) {
        int sectorSize = queryGeometry(diskNumber).bytesPerSector();
        if (sectorSize <= 0 || offset % sectorSize != 0 || length % sectorSize != 0) {
            throw new IllegalArgumentException(
                    "offset (" + offset + ") and length (" + length + ") must both be exact multiples of the "
                            + "sector size (" + sectorSize + ") for disk " + diskNumber);
        }

        WinNT.HANDLE handle = openPhysicalDrive(diskNumber);
        try {
            byte[] buffer = new byte[length];
            WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
            overlapped.Offset = (int) (offset & 0xFFFFFFFFL);
            overlapped.OffsetHigh = (int) (offset >>> 32);

            IntByReference bytesRead = new IntByReference();
            boolean ok = Kernel32.INSTANCE.ReadFile(handle, buffer, length, bytesRead, overlapped);
            if (!ok) {
                int error = Kernel32.INSTANCE.GetLastError();
                throw new NativeAccessException(
                        "ReadFile failed for disk " + diskNumber + " at offset " + offset, error);
            }
            if (bytesRead.getValue() != length) {
                throw new NativeAccessException(
                        "ReadFile returned " + bytesRead.getValue() + " of " + length + " requested bytes for "
                                + "disk " + diskNumber + " at offset " + offset,
                        0);
            }
            return buffer;
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    private void issueStoragePropertyQuery(
            WinNT.HANDLE handle, int diskNumber, int propertyId, Pointer output, int outputSize) {
        StoragePropertyQueryStruct query = new StoragePropertyQueryStruct();
        query.propertyId = propertyId;
        query.queryType = StorageIoctl.QUERY_TYPE_PROPERTY_STANDARD_QUERY;
        query.write();

        IntByReference bytesReturned = new IntByReference();
        boolean ok = Kernel32.INSTANCE.DeviceIoControl(
                handle,
                StorageIoctl.IOCTL_STORAGE_QUERY_PROPERTY,
                query.getPointer(),
                query.size(),
                output,
                outputSize,
                bytesReturned,
                null);
        if (!ok) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new NativeAccessException(
                    "IOCTL_STORAGE_QUERY_PROPERTY failed for disk " + diskNumber + " (property " + propertyId + ")",
                    error);
        }
    }

    private static String readOffsetString(Memory buffer, int offset) {
        if (offset == 0) {
            return null;
        }
        String value = buffer.getString(offset, "US-ASCII").trim();
        return value.isEmpty() ? null : value;
    }

    private static WinNT.HANDLE openPhysicalDrive(int diskNumber) {
        return openForRead(physicalDrivePath(diskNumber));
    }

    private static WinNT.HANDLE openVolume(String volumePath) {
        return openForRead("\\\\.\\" + volumePath);
    }

    private static WinNT.HANDLE openForRead(String path) {
        WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(
                path,
                WinNT.GENERIC_READ,
                WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                null,
                WinNT.OPEN_EXISTING,
                0,
                null);
        if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new NativeAccessException("Could not open " + path, error);
        }
        return handle;
    }

    private static String physicalDrivePath(int diskNumber) {
        return "\\\\.\\PhysicalDrive" + diskNumber;
    }
}
