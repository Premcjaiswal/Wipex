package com.zerowipe.sanitize.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure, no-I/O parts of {@link RawDiskSpike} directly - argument
 * parsing and target-to-disk-number extraction. The rest of the flow
 * (PowerShell device lookup, raw JNA I/O) can't run outside real Windows
 * and isn't exercised here; this suite exists specifically because a
 * hand-rolled regex for the target format was wrong on the first attempt
 * and only caught by writing this out and reasoning through it carefully -
 * exactly the kind of thing worth locking in with a test rather than
 * trusting by inspection.
 */
class RawDiskSpikeTest {

    @Test
    void parsesAStraightforwardTarget() {
        assertEquals(OptionalInt.of(2), RawDiskSpike.parseDiskNumber("\\\\.\\PhysicalDrive2"));
    }

    @Test
    void parsesDiskZero() {
        // Parsing itself doesn't refuse disk 0 - that's a separate check in run().
        assertEquals(OptionalInt.of(0), RawDiskSpike.parseDiskNumber("\\\\.\\PhysicalDrive0"));
    }

    @Test
    void parsesAMultiDigitDiskNumber() {
        assertEquals(OptionalInt.of(15), RawDiskSpike.parseDiskNumber("\\\\.\\PhysicalDrive15"));
    }

    @Test
    void rejectsNull() {
        assertTrue(RawDiskSpike.parseDiskNumber(null).isEmpty());
    }

    @Test
    void rejectsAMissingPrefix() {
        assertTrue(RawDiskSpike.parseDiskNumber("PhysicalDrive2").isEmpty());
    }

    @Test
    void rejectsAWrongPrefix() {
        assertTrue(RawDiskSpike.parseDiskNumber("\\\\.\\PhysicalDriveX\\2").isEmpty());
    }

    @Test
    void rejectsAnEmptySuffix() {
        assertTrue(RawDiskSpike.parseDiskNumber("\\\\.\\PhysicalDrive").isEmpty());
    }

    @Test
    void rejectsANonNumericSuffix() {
        assertTrue(RawDiskSpike.parseDiskNumber("\\\\.\\PhysicalDriveABC").isEmpty());
    }

    @Test
    void rejectsTrailingCharactersAfterTheDigits() {
        assertTrue(RawDiskSpike.parseDiskNumber("\\\\.\\PhysicalDrive2x").isEmpty());
    }

    @Test
    void rejectsANegativeLookingNumber() {
        assertTrue(RawDiskSpike.parseDiskNumber("\\\\.\\PhysicalDrive-1").isEmpty());
    }

    @Test
    void rejectsWrongCasing() {
        // Deliberately strict: the operator must type the exact format the
        // spike's own usage message shows, not rely on Windows path
        // case-insensitivity.
        assertTrue(RawDiskSpike.parseDiskNumber("\\\\.\\physicaldrive2").isEmpty());
    }

    @Test
    void runReturnsExitCodeTwoWhenTargetIsMissing() {
        assertEquals(2, RawDiskSpike.run(new String[] {"--confirm=SN123"}));
    }

    @Test
    void runReturnsExitCodeTwoWhenConfirmIsMissing() {
        assertEquals(2, RawDiskSpike.run(new String[] {"--target=\\\\.\\PhysicalDrive2"}));
    }

    @Test
    void runReturnsExitCodeTwoWhenTargetFormatIsInvalid() {
        assertEquals(
                2, RawDiskSpike.run(new String[] {"--target=D:\\notadisk", "--confirm=SN123"}));
    }

    @Test
    void runReturnsExitCodeThreeForPhysicalDriveZeroWithoutTouchingPowerShell() {
        // This must be rejected before any PowerShell/device lookup happens,
        // so it's safe to assert here even though PowerShell isn't available
        // in this test environment.
        assertEquals(
                3, RawDiskSpike.run(new String[] {"--target=\\\\.\\PhysicalDrive0", "--confirm=SN123"}));
    }
}
