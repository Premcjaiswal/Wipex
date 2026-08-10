package com.zerowipe.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PowerShellQueryService#parseJsonArray(String, Class)}
 * directly against sample {@code ConvertTo-Json} output, without spawning a
 * real PowerShell process (not available in this environment). The
 * single-object-vs-array quirk is the main thing worth locking in here.
 */
class PowerShellQueryServiceTest {

    private final PowerShellQueryService service = new PowerShellQueryService();

    @Test
    void parsesAJsonArrayOfMultipleResults() {
        String json = """
                [
                  {"DeviceId": 0, "MediaType": "HDD", "HealthStatus": "Healthy"},
                  {"DeviceId": 1, "MediaType": "SSD", "HealthStatus": "Warning"}
                ]
                """;

        List<PhysicalDiskInfo> disks = service.parseJsonArray(json, PhysicalDiskInfo.class);

        assertEquals(2, disks.size());
        assertEquals(0, disks.get(0).deviceId());
        assertEquals("HDD", disks.get(0).mediaType());
        assertEquals(1, disks.get(1).deviceId());
        assertEquals("SSD", disks.get(1).mediaType());
    }

    @Test
    void parsesASingleJsonObjectAsAOneElementList() {
        // ConvertTo-Json emits a bare object, not a one-element array, when
        // the pipeline produces exactly one result - this is the quirk that
        // matters most here.
        String json = """
                {"DeviceId": 0, "MediaType": "HDD", "HealthStatus": "Healthy"}
                """;

        List<PhysicalDiskInfo> disks = service.parseJsonArray(json, PhysicalDiskInfo.class);

        assertEquals(1, disks.size());
        assertEquals(0, disks.get(0).deviceId());
        assertEquals("HDD", disks.get(0).mediaType());
    }

    @Test
    void matchesPascalCasePowerShellFieldsToCamelCaseRecordComponents() {
        String json = """
                {"DeviceId": 3, "Wear": 12, "PowerOnHours": 4500}
                """;

        List<StorageReliabilityCounterInfo> counters =
                service.parseJsonArray(json, StorageReliabilityCounterInfo.class);

        assertEquals(1, counters.size());
        assertEquals(3, counters.get(0).deviceId());
        assertEquals(12, counters.get(0).wear());
        assertEquals(4500L, counters.get(0).powerOnHours());
    }

    @Test
    void nullInputReturnsAnEmptyList() {
        assertTrue(service.parseJsonArray(null, PhysicalDiskInfo.class).isEmpty());
    }

    @Test
    void blankInputReturnsAnEmptyList() {
        assertTrue(service.parseJsonArray("   ", PhysicalDiskInfo.class).isEmpty());
    }

    @Test
    void malformedJsonReturnsAnEmptyListRatherThanThrowing() {
        assertTrue(service.parseJsonArray("{not valid json", PhysicalDiskInfo.class).isEmpty());
    }

    @Test
    void emptyArrayReturnsAnEmptyList() {
        assertTrue(service.parseJsonArray("[]", PhysicalDiskInfo.class).isEmpty());
    }

    @Test
    void unknownExtraPropertiesAreIgnoredRatherThanFailingParsing() {
        String json = """
                {"DeviceId": 0, "MediaType": "HDD", "HealthStatus": "Healthy", "SomeFutureField": "x"}
                """;

        List<PhysicalDiskInfo> disks = service.parseJsonArray(json, PhysicalDiskInfo.class);

        assertEquals(1, disks.size());
        assertEquals(0, disks.get(0).deviceId());
    }
}
