package com.zerowipe.policy;

import com.zerowipe.device.BusType;
import com.zerowipe.device.DeviceCapabilities;
import com.zerowipe.device.MediaType;
import com.zerowipe.device.PhysicalDevice;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Decides which sanitization methods are honestly valid for a device, per
 * NIST SP 800-88 Rev. 1. Clear, Purge and Destroy are alternative assurance
 * levels selected by media type and required confidentiality, not
 * sequential phases: this build can only execute Clear-level overwrite
 * methods (Zero Fill, DoD 5220.22-M 3-pass), so a request for Purge is
 * refused outright rather than silently downgraded to Clear.
 *
 * <p>{@link SanitizationMethod#PHYSICAL_DESTRUCTION} is a documentation
 * workflow only and is never placed in {@code permittedMethods} - it does
 * not depend on media type or device probing, so it is only touched here
 * when a decision refuses every method outright.
 */
@Service
public class SanitizationPolicyEngine {

    static final String USB_BRIDGE_WARNING =
            "Media type reporting through USB bridges is unreliable; verify manually.";

    static final String HDD_LEGACY_DOD_WARNING =
            "Multi-pass overwriting provides no measurable benefit over a single pass on modern drives. "
                    + "The DoD 5220.22-M overwrite matrix was removed from the NISPOM in 2007. Included for "
                    + "comparative benchmarking only.";

    static final String FLASH_ZERO_FILL_WARNING =
            "Wear levelling and over-provisioning mean some physical blocks are not host-addressable. "
                    + "Overwriting achieves NIST Clear but cannot achieve Purge on flash media.";

    static final String FLASH_DOD_DISCOURAGED_WARNING =
            "Multi-pass overwriting on flash media causes additional wear with no security benefit, and "
                    + "still cannot reach unmapped blocks.";

    /**
     * Decides the sanitization policy for one device. Never throws for a
     * refusable device - refusal is always a normal, explained outcome.
     *
     * @throws NullPointerException if any argument is null
     */
    public PolicyDecision decide(
            PhysicalDevice device, DeviceCapabilities capabilities, NistCategory requestedAssurance) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(requestedAssurance, "requestedAssurance");

        if (device.isSystemDisk()) {
            return refuseAllMethods(
                    RefusalReason.SYSTEM_DISK,
                    "Disk " + device.diskNumber() + " contains the active system volume or boot files; "
                            + "sanitizing it would destroy the running operating system.");
        }
        if (device.busType() == BusType.RAID) {
            return refuseAllMethods(
                    RefusalReason.RAID_VIRTUAL_DISK,
                    "Disk " + device.diskNumber() + " is reported as a RAID/virtual volume, not physical "
                            + "media directly addressable by this system; no sanitization claim would be valid.");
        }
        if (requestedAssurance == NistCategory.PURGE) {
            return refuseAllMethods(
                    RefusalReason.PURGE_NOT_SUPPORTED_IN_THIS_BUILD,
                    "Purge-level assurance was requested, but this build implements no Purge-capable "
                            + "command - ATA Secure Erase, NVMe Sanitize and Opal PSID Revert are all out of "
                            + "scope. Overwrite-based methods can only achieve Clear, which does not satisfy a "
                            + "Purge requirement. Use a device-level sanitization tool that implements one of "
                            + "those commands, or escalate to Destroy (physical destruction) if no such tool is "
                            + "available.");
        }
        if (device.mediaType() == MediaType.UNKNOWN) {
            return refuseOverwriteMethods(
                    RefusalReason.MEDIA_TYPE_UNKNOWN,
                    "The media type of disk " + device.diskNumber() + " could not be determined, so no "
                            + "overwrite method can be honestly recommended - whether flash-specific "
                            + "unmapped-block caveats apply is unknown.");
        }

        return decideOverwrite(device, capabilities);
    }

    private PolicyDecision decideOverwrite(PhysicalDevice device, DeviceCapabilities capabilities) {
        List<String> warnings = new ArrayList<>();
        String mediaRationale;

        if (device.mediaType() == MediaType.HDD) {
            warnings.add(HDD_LEGACY_DOD_WARNING);
            mediaRationale = "Disk " + device.diskNumber() + " is a hard disk drive. Zero Fill (single-pass) "
                    + "is recommended: NIST SP 800-88 Rev. 1 treats a single overwrite pass as sufficient for "
                    + "Clear on modern HDDs. DoD 5220.22-M 3-pass is also offered, for legacy/comparative "
                    + "purposes only.";
        } else {
            warnings.add(FLASH_ZERO_FILL_WARNING);
            warnings.add(FLASH_DOD_DISCOURAGED_WARNING);
            mediaRationale = "Disk " + device.diskNumber() + " is flash-based (" + device.mediaType() + "). "
                    + "Zero Fill is recommended, achieving NIST Clear. DoD 5220.22-M 3-pass is offered for "
                    + "comparison but discouraged on flash media - see warnings for why.";
        }

        if (device.busType() == BusType.USB) {
            warnings.add(USB_BRIDGE_WARNING);
        }

        Long reallocated = capabilities.reallocatedSectorCount();
        if (reallocated != null && reallocated > 0) {
            warnings.add(reallocated + " reallocated sector(s) detected; remapped sectors are not "
                    + "host-addressable and cannot be reached by overwriting.");
        }

        if (!isHealthy(capabilities.smartHealthStatus())) {
            warnings.add("SMART health status is '" + describeHealth(capabilities.smartHealthStatus())
                    + "'; the sanitization operation may fail mid-run.");
        }

        String rationale = mediaRationale
                + " Achievable assurance: CLEAR. This build implements no Purge-capable command, so Purge is "
                + "not achievable on any media.";

        return new PolicyDecision(
                SanitizationMethod.ZERO_FILL,
                List.of(SanitizationMethod.ZERO_FILL, SanitizationMethod.DOD_3PASS_LEGACY),
                Map.of(),
                NistCategory.CLEAR,
                List.copyOf(warnings),
                rationale);
    }

    private static boolean isHealthy(String smartHealthStatus) {
        return "healthy".equalsIgnoreCase(smartHealthStatus);
    }

    private static String describeHealth(String smartHealthStatus) {
        return smartHealthStatus == null ? "UNKNOWN" : smartHealthStatus;
    }

    /** Refuses every {@link SanitizationMethod}, including physical destruction. */
    private static PolicyDecision refuseAllMethods(RefusalReason reason, String rationale) {
        Map<SanitizationMethod, RefusalReason> refused = new EnumMap<>(SanitizationMethod.class);
        for (SanitizationMethod method : SanitizationMethod.values()) {
            refused.put(method, reason);
        }
        return new PolicyDecision(null, List.of(), Map.copyOf(refused), null, List.of(), rationale);
    }

    /** Refuses only the overwrite methods; physical destruction is untouched. */
    private static PolicyDecision refuseOverwriteMethods(RefusalReason reason, String rationale) {
        Map<SanitizationMethod, RefusalReason> refused = new EnumMap<>(SanitizationMethod.class);
        refused.put(SanitizationMethod.ZERO_FILL, reason);
        refused.put(SanitizationMethod.DOD_3PASS_LEGACY, reason);
        return new PolicyDecision(null, List.of(), Map.copyOf(refused), null, List.of(), rationale);
    }
}
