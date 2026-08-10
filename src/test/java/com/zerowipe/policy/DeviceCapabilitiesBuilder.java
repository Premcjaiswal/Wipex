package com.zerowipe.policy;

import com.zerowipe.device.DeviceCapabilities;

/**
 * Test-only fluent builder for {@link DeviceCapabilities}, defaulting to a
 * healthy device with no reallocated sectors so each test only overrides
 * the field(s) it cares about.
 */
final class DeviceCapabilitiesBuilder {

    private boolean supportsTrim = false;
    private Long reallocatedSectorCount = 0L;
    private String smartHealthStatus = "Healthy";
    private Integer wearPercentage = null;
    private Long powerOnHours = null;

    static DeviceCapabilitiesBuilder aCapability() {
        return new DeviceCapabilitiesBuilder();
    }

    DeviceCapabilitiesBuilder supportsTrim(boolean value) {
        this.supportsTrim = value;
        return this;
    }

    DeviceCapabilitiesBuilder reallocatedSectorCount(Long value) {
        this.reallocatedSectorCount = value;
        return this;
    }

    DeviceCapabilitiesBuilder smartHealthStatus(String value) {
        this.smartHealthStatus = value;
        return this;
    }

    DeviceCapabilitiesBuilder wearPercentage(Integer value) {
        this.wearPercentage = value;
        return this;
    }

    DeviceCapabilitiesBuilder powerOnHours(Long value) {
        this.powerOnHours = value;
        return this;
    }

    DeviceCapabilities build() {
        return new DeviceCapabilities(
                supportsTrim, reallocatedSectorCount, smartHealthStatus, wearPercentage, powerOnHours);
    }
}
