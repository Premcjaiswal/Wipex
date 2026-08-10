package com.zerowipe.policy;

import com.zerowipe.device.BusType;
import com.zerowipe.device.MediaType;
import com.zerowipe.device.PhysicalDevice;

/**
 * Test-only fluent builder for {@link PhysicalDevice}, defaulting to a
 * plain, permitted SATA HDD so each test only overrides the field(s) it
 * cares about.
 */
final class PhysicalDeviceBuilder {

    private int diskNumber = 1;
    private String model = "Test Model";
    private String serialNumber = "SN-TEST-0001";
    private String firmwareRevision = "1.0";
    private BusType busType = BusType.SATA;
    private MediaType mediaType = MediaType.HDD;
    private long capacityBytes = 500_000_000_000L;
    private int bytesPerSector = 512;
    private boolean systemDisk = false;
    private boolean removable = false;

    static PhysicalDeviceBuilder aDevice() {
        return new PhysicalDeviceBuilder();
    }

    PhysicalDeviceBuilder diskNumber(int value) {
        this.diskNumber = value;
        return this;
    }

    PhysicalDeviceBuilder busType(BusType value) {
        this.busType = value;
        return this;
    }

    PhysicalDeviceBuilder mediaType(MediaType value) {
        this.mediaType = value;
        return this;
    }

    PhysicalDeviceBuilder systemDisk(boolean value) {
        this.systemDisk = value;
        return this;
    }

    PhysicalDeviceBuilder removable(boolean value) {
        this.removable = value;
        return this;
    }

    PhysicalDevice build() {
        return new PhysicalDevice(
                diskNumber,
                model,
                serialNumber,
                firmwareRevision,
                busType,
                mediaType,
                capacityBytes,
                bytesPerSector,
                systemDisk,
                removable);
    }
}
