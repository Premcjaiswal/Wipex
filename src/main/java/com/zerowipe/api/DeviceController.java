package com.zerowipe.api;

import com.zerowipe.device.DeviceDiscoveryService;
import com.zerowipe.device.PhysicalDevice;
import com.zerowipe.nativelayer.NativeAccessException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    @ExceptionHandler(NativeAccessException.class)
    public ResponseEntity<Map<String, Object>> handleNativeAccessException(NativeAccessException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "native_access_failed",
                        "message", e.getMessage(),
                        "win32ErrorCode", e.win32ErrorCode()));
    }
}
