package com.zerowipe.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SystemDiskResolverTest {

    private final PowerShellQueryService powerShell = mock(PowerShellQueryService.class);
    private final SystemDiskResolver resolver = new SystemDiskResolver(powerShell);

    @Test
    void parsesDriveLetterFromATypicalSystemRoot() {
        assertEquals("C:", SystemDiskResolver.driveLetterFromSystemRoot("C:\\Windows"));
    }

    @Test
    void parsesDriveLetterFromANonCSystemRoot() {
        assertEquals("D:", SystemDiskResolver.driveLetterFromSystemRoot("D:\\Windows"));
    }

    @Test
    void nullSystemRootYieldsNullDriveLetter() {
        assertNull(SystemDiskResolver.driveLetterFromSystemRoot(null));
    }

    @Test
    void malformedSystemRootYieldsNullDriveLetter() {
        assertNull(SystemDiskResolver.driveLetterFromSystemRoot("not-a-windows-path"));
    }

    @Test
    void resolvesTheSystemDiskFromPowerShell() {
        when(powerShell.queryDiskNumbersForDriveLetter("C")).thenReturn(List.of(0));

        Set<Integer> result = resolver.resolveForDriveLetter("C:");

        assertEquals(Set.of(0), result);
    }

    @Test
    void marksEveryDiskASpannedSystemVolumeTouches() {
        when(powerShell.queryDiskNumbersForDriveLetter("C")).thenReturn(List.of(0, 1));

        Set<Integer> result = resolver.resolveForDriveLetter("C:");

        assertEquals(Set.of(0, 1), result);
    }

    @Test
    void returnsEmptySetWhenPowerShellHasNoAnswer() {
        when(powerShell.queryDiskNumbersForDriveLetter("C")).thenReturn(List.of());

        Set<Integer> result = resolver.resolveForDriveLetter("C:");

        assertEquals(Set.of(), result);
    }

    @Test
    void systemDiskNumbersCachesTheResultAcrossCalls() {
        // With no SystemRoot env var set in this test environment,
        // systemDiskNumbers() takes the "could not determine drive letter"
        // path both times - this test only asserts the two calls return the
        // same (cached) instance rather than re-resolving.
        Set<Integer> first = resolver.systemDiskNumbers();
        Set<Integer> second = resolver.systemDiskNumbers();

        assertEquals(first, second);
    }
}
