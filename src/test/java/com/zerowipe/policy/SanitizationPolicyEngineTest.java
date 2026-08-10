package com.zerowipe.policy;

import static com.zerowipe.policy.DeviceCapabilitiesBuilder.aCapability;
import static com.zerowipe.policy.PhysicalDeviceBuilder.aDevice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zerowipe.device.BusType;
import com.zerowipe.device.DeviceCapabilities;
import com.zerowipe.device.MediaType;
import com.zerowipe.device.PhysicalDevice;
import java.util.List;
import org.junit.jupiter.api.Test;

class SanitizationPolicyEngineTest {

    private final SanitizationPolicyEngine engine = new SanitizationPolicyEngine();

    // ---------------------------------------------------------------
    // Null argument handling
    // ---------------------------------------------------------------

    @Test
    void nullDeviceThrows() {
        assertThrows(
                NullPointerException.class,
                () -> engine.decide(null, aCapability().build(), NistCategory.CLEAR));
    }

    @Test
    void nullCapabilitiesThrows() {
        assertThrows(
                NullPointerException.class,
                () -> engine.decide(aDevice().build(), null, NistCategory.CLEAR));
    }

    @Test
    void nullRequestedAssuranceThrows() {
        assertThrows(
                NullPointerException.class,
                () -> engine.decide(aDevice().build(), aCapability().build(), null));
    }

    // ---------------------------------------------------------------
    // Hard refusals
    // ---------------------------------------------------------------

    @Test
    void systemDiskRefusesEveryMethod() {
        PhysicalDevice device = aDevice().systemDisk(true).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertNull(decision.recommendedMethod());
        assertTrue(decision.permittedMethods().isEmpty());
        assertNull(decision.achievableAssuranceLevel());
        for (SanitizationMethod method : SanitizationMethod.values()) {
            assertEquals(RefusalReason.SYSTEM_DISK, decision.refusedMethods().get(method));
        }
    }

    @Test
    void systemDiskRationaleMentionsTheDiskNumber() {
        PhysicalDevice device = aDevice().diskNumber(7).systemDisk(true).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertTrue(decision.rationale().contains("7"));
    }

    @Test
    void raidBusRefusesEveryMethod() {
        PhysicalDevice device = aDevice().busType(BusType.RAID).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertNull(decision.recommendedMethod());
        assertTrue(decision.permittedMethods().isEmpty());
        assertNull(decision.achievableAssuranceLevel());
        for (SanitizationMethod method : SanitizationMethod.values()) {
            assertEquals(RefusalReason.RAID_VIRTUAL_DISK, decision.refusedMethods().get(method));
        }
    }

    @Test
    void raidRationaleExplainsItIsVirtualMedia() {
        PhysicalDevice device = aDevice().busType(BusType.RAID).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertTrue(decision.rationale().toLowerCase().contains("virtual"));
    }

    @Test
    void systemDiskTakesPriorityOverRaid() {
        PhysicalDevice device = aDevice().systemDisk(true).busType(BusType.RAID).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertEquals(RefusalReason.SYSTEM_DISK, decision.refusedMethods().get(SanitizationMethod.ZERO_FILL));
    }

    @Test
    void systemDiskTakesPriorityOverPurgeRequest() {
        PhysicalDevice device = aDevice().systemDisk(true).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.PURGE);

        assertEquals(RefusalReason.SYSTEM_DISK, decision.refusedMethods().get(SanitizationMethod.ZERO_FILL));
    }

    // ---------------------------------------------------------------
    // Scope refusal: Purge requested
    // ---------------------------------------------------------------

    @Test
    void purgeRequestRefusesEveryMethod() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.PURGE);

        assertNull(decision.recommendedMethod());
        assertTrue(decision.permittedMethods().isEmpty());
        assertNull(decision.achievableAssuranceLevel());
        for (SanitizationMethod method : SanitizationMethod.values()) {
            assertEquals(
                    RefusalReason.PURGE_NOT_SUPPORTED_IN_THIS_BUILD, decision.refusedMethods().get(method));
        }
    }

    @Test
    void purgeRequestRationaleExplainsOutOfScopeCommandsAndEscalation() {
        PhysicalDevice device = aDevice().build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.PURGE);

        String rationale = decision.rationale();
        assertTrue(rationale.contains("Secure Erase"));
        assertTrue(rationale.contains("NVMe Sanitize"));
        assertTrue(rationale.contains("Opal"));
        assertTrue(rationale.toLowerCase().contains("destroy"));
    }

    @Test
    void purgeRequestOverridesMediaSpecificHandlingForSsd() {
        PhysicalDevice device = aDevice().mediaType(MediaType.SSD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.PURGE);

        assertEquals(
                RefusalReason.PURGE_NOT_SUPPORTED_IN_THIS_BUILD,
                decision.refusedMethods().get(SanitizationMethod.ZERO_FILL));
    }

    @Test
    void purgeRequestOverridesMediaTypeUnknownHandling() {
        PhysicalDevice device = aDevice().mediaType(MediaType.UNKNOWN).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.PURGE);

        assertEquals(
                RefusalReason.PURGE_NOT_SUPPORTED_IN_THIS_BUILD,
                decision.refusedMethods().get(SanitizationMethod.ZERO_FILL));
    }

    // ---------------------------------------------------------------
    // Media type unknown
    // ---------------------------------------------------------------

    @Test
    void unknownMediaTypeRefusesOnlyOverwriteMethods() {
        PhysicalDevice device = aDevice().mediaType(MediaType.UNKNOWN).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertEquals(
                RefusalReason.MEDIA_TYPE_UNKNOWN,
                decision.refusedMethods().get(SanitizationMethod.ZERO_FILL));
        assertEquals(
                RefusalReason.MEDIA_TYPE_UNKNOWN,
                decision.refusedMethods().get(SanitizationMethod.DOD_3PASS_LEGACY));
        assertFalse(decision.refusedMethods().containsKey(SanitizationMethod.PHYSICAL_DESTRUCTION));
    }

    @Test
    void unknownMediaTypeLeavesNothingPermitted() {
        PhysicalDevice device = aDevice().mediaType(MediaType.UNKNOWN).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertNull(decision.recommendedMethod());
        assertTrue(decision.permittedMethods().isEmpty());
        assertNull(decision.achievableAssuranceLevel());
    }

    // ---------------------------------------------------------------
    // HDD
    // ---------------------------------------------------------------

    @Test
    void hddPermitsBothOverwriteMethods() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertEquals(
                List.of(SanitizationMethod.ZERO_FILL, SanitizationMethod.DOD_3PASS_LEGACY),
                decision.permittedMethods());
        assertTrue(decision.refusedMethods().isEmpty());
    }

    @Test
    void hddRecommendsZeroFillAtClear() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertEquals(SanitizationMethod.ZERO_FILL, decision.recommendedMethod());
        assertEquals(NistCategory.CLEAR, decision.achievableAssuranceLevel());
    }

    @Test
    void hddWarnsThatDodIsLegacy() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.HDD_LEGACY_DOD_WARNING));
    }

    @Test
    void hddDoesNotCarryFlashSpecificWarnings() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertFalse(decision.warnings().contains(SanitizationPolicyEngine.FLASH_ZERO_FILL_WARNING));
        assertFalse(decision.warnings().contains(SanitizationPolicyEngine.FLASH_DOD_DISCOURAGED_WARNING));
    }

    @Test
    void hddRationaleIsNotBlankAndNamesTheMethodAndAssurance() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertFalse(decision.rationale().isBlank());
        assertTrue(decision.rationale().contains("Zero Fill"));
        assertTrue(decision.rationale().contains("CLEAR"));
    }

    // ---------------------------------------------------------------
    // SSD / NVMe
    // ---------------------------------------------------------------

    @Test
    void ssdPermitsBothOverwriteMethods() {
        PhysicalDevice device = aDevice().mediaType(MediaType.SSD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertEquals(
                List.of(SanitizationMethod.ZERO_FILL, SanitizationMethod.DOD_3PASS_LEGACY),
                decision.permittedMethods());
        assertEquals(SanitizationMethod.ZERO_FILL, decision.recommendedMethod());
        assertEquals(NistCategory.CLEAR, decision.achievableAssuranceLevel());
    }

    @Test
    void ssdWarnsAboutUnreachableFlashBlocksOnZeroFill() {
        PhysicalDevice device = aDevice().mediaType(MediaType.SSD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.FLASH_ZERO_FILL_WARNING));
    }

    @Test
    void ssdDiscouragesMultiPassOverwrite() {
        PhysicalDevice device = aDevice().mediaType(MediaType.SSD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.FLASH_DOD_DISCOURAGED_WARNING));
    }

    @Test
    void nvmeMediaTypeGetsTheSameFlashWarningsAsSsd() {
        PhysicalDevice device = aDevice().mediaType(MediaType.NVME).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertEquals(SanitizationMethod.ZERO_FILL, decision.recommendedMethod());
        assertEquals(NistCategory.CLEAR, decision.achievableAssuranceLevel());
        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.FLASH_ZERO_FILL_WARNING));
        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.FLASH_DOD_DISCOURAGED_WARNING));
    }

    @Test
    void ssdDoesNotCarryTheHddLegacyWarning() {
        PhysicalDevice device = aDevice().mediaType(MediaType.SSD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertFalse(decision.warnings().contains(SanitizationPolicyEngine.HDD_LEGACY_DOD_WARNING));
    }

    // ---------------------------------------------------------------
    // USB bridge
    // ---------------------------------------------------------------

    @Test
    void usbBusAddsUnreliableReportingWarningButStillPermits() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).busType(BusType.USB).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.USB_BRIDGE_WARNING));
        assertEquals(SanitizationMethod.ZERO_FILL, decision.recommendedMethod());
        assertFalse(decision.permittedMethods().isEmpty());
    }

    @Test
    void nonUsbBusDoesNotAddUsbWarning() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).busType(BusType.SATA).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertFalse(decision.warnings().contains(SanitizationPolicyEngine.USB_BRIDGE_WARNING));
    }

    @Test
    void usbSsdCarriesBothUsbAndFlashWarnings() {
        PhysicalDevice device = aDevice().mediaType(MediaType.SSD).busType(BusType.USB).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.CLEAR);

        assertEquals(3, decision.warnings().size());
        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.USB_BRIDGE_WARNING));
        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.FLASH_ZERO_FILL_WARNING));
        assertTrue(decision.warnings().contains(SanitizationPolicyEngine.FLASH_DOD_DISCOURAGED_WARNING));
    }

    // ---------------------------------------------------------------
    // Reallocated sectors
    // ---------------------------------------------------------------

    @Test
    void reallocatedSectorsAboveZeroAddsWarningWithTheCount() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();
        DeviceCapabilities capabilities = aCapability().reallocatedSectorCount(5L).build();

        PolicyDecision decision = engine.decide(device, capabilities, NistCategory.CLEAR);

        assertTrue(decision.warnings().stream().anyMatch(w -> w.contains("5") && w.contains("reallocated")));
    }

    @Test
    void zeroReallocatedSectorsAddsNoWarning() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();
        DeviceCapabilities capabilities = aCapability().reallocatedSectorCount(0L).build();

        PolicyDecision decision = engine.decide(device, capabilities, NistCategory.CLEAR);

        assertFalse(decision.warnings().stream().anyMatch(w -> w.contains("reallocated")));
    }

    @Test
    void nullReallocatedSectorCountAddsNoWarning() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();
        DeviceCapabilities capabilities = aCapability().reallocatedSectorCount(null).build();

        PolicyDecision decision = engine.decide(device, capabilities, NistCategory.CLEAR);

        assertFalse(decision.warnings().stream().anyMatch(w -> w.contains("reallocated")));
    }

    // ---------------------------------------------------------------
    // SMART health
    // ---------------------------------------------------------------

    @Test
    void healthySmartStatusAddsNoWarning() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();
        DeviceCapabilities capabilities = aCapability().smartHealthStatus("Healthy").build();

        PolicyDecision decision = engine.decide(device, capabilities, NistCategory.CLEAR);

        assertFalse(decision.warnings().stream().anyMatch(w -> w.contains("SMART")));
    }

    @Test
    void healthySmartStatusIsCaseInsensitive() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();
        DeviceCapabilities capabilities = aCapability().smartHealthStatus("HEALTHY").build();

        PolicyDecision decision = engine.decide(device, capabilities, NistCategory.CLEAR);

        assertFalse(decision.warnings().stream().anyMatch(w -> w.contains("SMART")));
    }

    @Test
    void unhealthySmartStatusAddsWarningNamingTheStatus() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();
        DeviceCapabilities capabilities = aCapability().smartHealthStatus("Warning").build();

        PolicyDecision decision = engine.decide(device, capabilities, NistCategory.CLEAR);

        assertTrue(decision.warnings().stream().anyMatch(w -> w.contains("SMART") && w.contains("Warning")));
    }

    @Test
    void nullSmartStatusIsTreatedAsNotHealthy() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();
        DeviceCapabilities capabilities = aCapability().smartHealthStatus(null).build();

        PolicyDecision decision = engine.decide(device, capabilities, NistCategory.CLEAR);

        assertTrue(decision.warnings().stream().anyMatch(w -> w.contains("SMART") && w.contains("UNKNOWN")));
    }

    // ---------------------------------------------------------------
    // Combined scenario and DESTROY fallthrough
    // ---------------------------------------------------------------

    @Test
    void combinedWarningsAllAppearTogetherWithoutRefusingTheDevice() {
        PhysicalDevice device = aDevice().mediaType(MediaType.SSD).busType(BusType.USB).build();
        DeviceCapabilities capabilities =
                aCapability().reallocatedSectorCount(3L).smartHealthStatus("Unhealthy").build();

        PolicyDecision decision = engine.decide(device, capabilities, NistCategory.CLEAR);

        assertEquals(5, decision.warnings().size());
        assertEquals(SanitizationMethod.ZERO_FILL, decision.recommendedMethod());
        assertEquals(NistCategory.CLEAR, decision.achievableAssuranceLevel());
        assertTrue(decision.refusedMethods().isEmpty());
    }

    @Test
    void requestingDestroyOnAnOrdinaryDeviceStillOnlyAchievesClear() {
        PhysicalDevice device = aDevice().mediaType(MediaType.HDD).build();

        PolicyDecision decision = engine.decide(device, aCapability().build(), NistCategory.DESTROY);

        assertEquals(SanitizationMethod.ZERO_FILL, decision.recommendedMethod());
        assertEquals(NistCategory.CLEAR, decision.achievableAssuranceLevel());
    }
}
