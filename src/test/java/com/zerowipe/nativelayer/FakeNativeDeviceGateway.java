package com.zerowipe.nativelayer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only {@link NativeDeviceGateway} backed by in-memory fixtures,
 * configured via fluent {@code with*} builder methods. Any query for a
 * disk number (or volume path) that hasn't been configured throws
 * {@link NativeAccessException}, mirroring what the real gateway does for
 * a disk that doesn't exist.
 */
public final class FakeNativeDeviceGateway implements NativeDeviceGateway {

    private final Map<Integer, DeviceDescriptor> deviceDescriptors = new LinkedHashMap<>();
    private final Map<Integer, SeekPenaltyInfo> seekPenalties = new HashMap<>();
    private final Map<Integer, TrimInfo> trimInfos = new HashMap<>();
    private final Map<Integer, AdapterDescriptor> adapterDescriptors = new HashMap<>();
    private final Map<Integer, DriveGeometry> geometries = new HashMap<>();
    private final Map<String, List<Integer>> volumeDiskExtents = new HashMap<>();
    private final Map<Integer, byte[]> sectorData = new HashMap<>();

    public FakeNativeDeviceGateway withDeviceDescriptor(int diskNumber, DeviceDescriptor descriptor) {
        deviceDescriptors.put(diskNumber, descriptor);
        return this;
    }

    public FakeNativeDeviceGateway withSeekPenalty(int diskNumber, SeekPenaltyInfo seekPenalty) {
        seekPenalties.put(diskNumber, seekPenalty);
        return this;
    }

    public FakeNativeDeviceGateway withTrimInfo(int diskNumber, TrimInfo trimInfo) {
        trimInfos.put(diskNumber, trimInfo);
        return this;
    }

    public FakeNativeDeviceGateway withAdapterDescriptor(int diskNumber, AdapterDescriptor adapterDescriptor) {
        adapterDescriptors.put(diskNumber, adapterDescriptor);
        return this;
    }

    public FakeNativeDeviceGateway withGeometry(int diskNumber, DriveGeometry geometry) {
        geometries.put(diskNumber, geometry);
        return this;
    }

    public FakeNativeDeviceGateway withVolumeDiskExtents(String volumePath, List<Integer> diskNumbers) {
        volumeDiskExtents.put(volumePath, diskNumbers);
        return this;
    }

    public FakeNativeDeviceGateway withSectorData(int diskNumber, byte[] data) {
        sectorData.put(diskNumber, data);
        return this;
    }

    /** Convenience: registers descriptor, seek penalty, trim, adapter and geometry for one disk at once. */
    public FakeNativeDeviceGateway withDevice(
            int diskNumber,
            DeviceDescriptor descriptor,
            SeekPenaltyInfo seekPenalty,
            TrimInfo trimInfo,
            AdapterDescriptor adapterDescriptor,
            DriveGeometry geometry) {
        return withDeviceDescriptor(diskNumber, descriptor)
                .withSeekPenalty(diskNumber, seekPenalty)
                .withTrimInfo(diskNumber, trimInfo)
                .withAdapterDescriptor(diskNumber, adapterDescriptor)
                .withGeometry(diskNumber, geometry);
    }

    @Override
    public List<Integer> enumerateDiskNumbers() {
        return List.copyOf(deviceDescriptors.keySet());
    }

    @Override
    public DeviceDescriptor queryDeviceProperty(int diskNumber) {
        return require(deviceDescriptors, diskNumber, "device descriptor");
    }

    @Override
    public SeekPenaltyInfo querySeekPenalty(int diskNumber) {
        return require(seekPenalties, diskNumber, "seek penalty info");
    }

    @Override
    public TrimInfo queryTrimSupport(int diskNumber) {
        return require(trimInfos, diskNumber, "trim info");
    }

    @Override
    public AdapterDescriptor queryAdapter(int diskNumber) {
        return require(adapterDescriptors, diskNumber, "adapter descriptor");
    }

    @Override
    public DriveGeometry queryGeometry(int diskNumber) {
        return require(geometries, diskNumber, "geometry");
    }

    @Override
    public List<Integer> queryVolumeDiskExtents(String volumePath) {
        return volumeDiskExtents.getOrDefault(volumePath, List.of());
    }

    @Override
    public VolumeLockHandles lockAndDismountVolumes(int diskNumber) {
        throw new UnsupportedOperationException("lockAndDismountVolumes is not implemented in this phase");
    }

    @Override
    public void writeSectors(int diskNumber, long offset, byte[] buffer) {
        throw new UnsupportedOperationException("writeSectors is not implemented in this phase");
    }

    @Override
    public byte[] readSectors(int diskNumber, long offset, int length) {
        byte[] backing = require(sectorData, diskNumber, "sector data");
        return Arrays.copyOfRange(backing, (int) offset, (int) offset + length);
    }

    private static <T> T require(Map<Integer, T> fixtures, int diskNumber, String what) {
        T value = fixtures.get(diskNumber);
        if (value == null) {
            throw new NativeAccessException("No fake " + what + " configured for disk " + diskNumber, 0);
        }
        return value;
    }
}
