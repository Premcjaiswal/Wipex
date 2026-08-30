package com.zerowipe.api;

import com.zerowipe.device.DeviceDiscoveryService;
import com.zerowipe.device.PhysicalDevice;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceDiscoveryService deviceDiscoveryService;

    public DeviceController(DeviceDiscoveryService deviceDiscoveryService) {
        this.deviceDiscoveryService = deviceDiscoveryService;
    }

    @GetMapping
    public List<PhysicalDevice> listDevices() {
        return deviceDiscoveryService.discoverDevices();
    }
}
