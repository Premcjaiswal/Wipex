package com.zerowipe.device;

import com.zerowipe.nativelayer.DeviceDescriptor;
import com.zerowipe.nativelayer.DriveGeometry;
import com.zerowipe.nativelayer.NativeAccessException;
import com.zerowipe.nativelayer.NativeDeviceGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Enumerates physical drives and identifies them as {@link PhysicalDevice}
 * records, combining raw identity data from {@link NativeDeviceGateway}
 * with classification from {@link CapabilityDetector} and system-disk
 * status from {@link SystemDiskResolver}.
 *
 * <p>A disk that fails to answer any of the required native queries is
 * skipped (logged, not thrown) rather than failing the whole discovery
 * pass - one uncooperative or transient drive should not hide every other
 * device on the report.
 */
@Service
public class DeviceDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceDiscoveryService.class);

    private final NativeDeviceGateway nativeDeviceGateway;
    private final CapabilityDetector capabilityDetector;
    private final SystemDiskResolver systemDiskResolver;

    public DeviceDiscoveryService(
            NativeDeviceGateway nativeDeviceGateway,
            CapabilityDetector capabilityDetector,
            SystemDiskResolver systemDiskResolver) {
        this.nativeDeviceGateway = nativeDeviceGateway;
        this.capabilityDetector = capabilityDetector;
        this.systemDiskResolver = systemDiskResolver;
    }

    public List<PhysicalDevice> discoverDevices() {
        Set<Integer> systemDiskNumbers = systemDiskResolver.systemDiskNumbers();
        List<PhysicalDevice> devices = new ArrayList<>();
        for (int diskNumber : nativeDeviceGateway.enumerateDiskNumbers()) {
            try {
                devices.add(describeDevice(diskNumber, systemDiskNumbers));
            } catch (NativeAccessException e) {
                log.warn("Skipping disk {} during discovery: {}", diskNumber, e.getMessage());
            }
        }
        return List.copyOf(devices);
    }

    public Optional<PhysicalDevice> discoverDevice(int diskNumber) {
        try {
            return Optional.of(describeDevice(diskNumber, systemDiskResolver.systemDiskNumbers()));
        } catch (NativeAccessException e) {
            log.warn("Could not describe disk {}: {}", diskNumber, e.getMessage());
            return Optional.empty();
        }
    }

    private PhysicalDevice describeDevice(int diskNumber, Set<Integer> systemDiskNumbers) {
        DeviceDescriptor descriptor = nativeDeviceGateway.queryDeviceProperty(diskNumber);
        DriveGeometry geometry = nativeDeviceGateway.queryGeometry(diskNumber);
        BusType busType = capabilityDetector.detectBusType(diskNumber);
        MediaType mediaType = capabilityDetector.detectMediaType(diskNumber, busType);

        return new PhysicalDevice(
                diskNumber,
                descriptor.model(),
                descriptor.serialNumber(),
                descriptor.firmwareRevision(),
                busType,
                mediaType,
                geometry.totalCapacityBytes(),
                geometry.bytesPerSector(),
                systemDiskNumbers.contains(diskNumber),
                descriptor.removableMedia());
    }
}
