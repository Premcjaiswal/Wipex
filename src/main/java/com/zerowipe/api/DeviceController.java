package com.zerowipe.api;

import com.zerowipe.device.CapabilityDetector;
import com.zerowipe.device.DeviceCapabilities;
import com.zerowipe.device.DeviceDiscoveryService;
import com.zerowipe.device.PhysicalDevice;
import com.zerowipe.nativelayer.NativeAccessException;
import com.zerowipe.policy.NistCategory;
import com.zerowipe.policy.PolicyDecision;
import com.zerowipe.policy.SanitizationPolicyEngine;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceDiscoveryService deviceDiscoveryService;
    private final CapabilityDetector capabilityDetector;
    private final SanitizationPolicyEngine policyEngine;

    public DeviceController(
            DeviceDiscoveryService deviceDiscoveryService,
            CapabilityDetector capabilityDetector,
            SanitizationPolicyEngine policyEngine) {
        this.deviceDiscoveryService = deviceDiscoveryService;
        this.capabilityDetector = capabilityDetector;
        this.policyEngine = policyEngine;
    }

    @GetMapping
    public List<PhysicalDevice> listDevices() {
        return deviceDiscoveryService.discoverDevices();
    }

    @GetMapping("/{id}/capabilities")
    public DeviceCapabilities capabilities(@PathVariable("id") int id) {
        return capabilityDetector.detectCapabilities(id);
    }

    @GetMapping("/{id}/policy")
    public PolicyDecision policy(
            @PathVariable("id") int id,
            @RequestParam(name = "assurance", defaultValue = "CLEAR") NistCategory assurance) {
        PhysicalDevice device = deviceDiscoveryService.discoverDevice(id).orElseThrow(() -> new DeviceNotFoundException(id));
        DeviceCapabilities capabilities = capabilityDetector.detectCapabilities(id);
        return policyEngine.decide(device, capabilities, assurance);
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
