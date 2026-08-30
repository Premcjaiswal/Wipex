package com.zerowipe.sanitize.spike;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.zerowipe.config.ZeroWipeProperties;
import com.zerowipe.device.DeviceDiscoveryService;
import com.zerowipe.device.PhysicalDevice;
import com.zerowipe.device.PowerShellQueryService;
import com.zerowipe.device.SystemDiskResolver;
import com.zerowipe.safety.SafetyCheckException;
import com.zerowipe.safety.SafetyValidator;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1 proof of concept for raw physical-disk access. This is
 * deliberately isolated: no Spring annotations, no HTTP endpoint, not
 * callable from any controller, and never wired into the real
 * application. It proves exactly one thing - that this project can open
 * a physical drive, write to it, flush, and read the same bytes back -
 * before any real sanitizer implementation is built on top of the same
 * technique.
 *
 * <p>Run it directly against compiled classes, not the Spring Boot
 * repackaged jar (that jar's nested {@code BOOT-INF/lib} layout isn't a
 * plain classpath entry unless launched through Spring Boot's own
 * loader). First build the runtime classpath, then invoke this class
 * directly:
 *
 * <pre>{@code
 * mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
 * java -cp "target\classes;$(Get-Content cp.txt)" com.zerowipe.sanitize.spike.RawDiskSpike ^
 *     --target="\\.\PhysicalDrive2" --confirm=<the disk's actual serial number>
 * }</pre>
 *
 * <p>Flow: validate arguments -&gt; validate safety conditions (reusing
 * the real {@link SafetyValidator}) -&gt; open the target -&gt; write 4 KB
 * at offset 0 -&gt; flush -&gt; read the same 4 KB back -&gt; compare
 * byte-for-byte -&gt; exit. Nothing loops, nothing else is written -
 * this is not a wipe.
 *
 * <p><b>Test order (do not skip steps):</b> an unformatted/unpartitioned
 * VHD first ({@code New-VHD} + {@code Mount-VHD} - no mounted volume
 * means no volume-locking complexity yet), a dedicated disposable USB
 * second, never the system disk, never a disk with data that matters.
 * Windows will refuse a raw write to a disk with a mounted, in-use
 * filesystem ({@code ERROR_ACCESS_DENIED}) - that's expected for a real
 * USB with a filesystem on it; this phase does not implement
 * {@code FSCTL_LOCK_VOLUME}/{@code FSCTL_DISMOUNT_VOLUME} yet.
 *
 * <p>Exit codes: {@code 0} verified pass; {@code 2} bad/missing
 * arguments; {@code 3} a safety check refused the target; {@code 4} the
 * raw I/O failed or the read-back didn't match what was written.
 */
public final class RawDiskSpike {

    private static final Logger log = LoggerFactory.getLogger(RawDiskSpike.class);

    /** The literal path prefix a valid --target must start with, e.g. {@code \\.\PhysicalDrive2}. */
    private static final String PHYSICAL_DRIVE_PREFIX = "\\\\.\\PhysicalDrive";

    /**
     * ~16 GiB. A disposable test VHD/USB is expected to be small; a real
     * production drive is refused outright rather than risking a spike
     * bug against something that matters.
     */
    private static final long CAPACITY_CEILING_BYTES = 16L * 1024 * 1024 * 1024;

    /**
     * 4 KB at offset 0 is sector-aligned regardless of whether the target
     * uses 512-byte or 4096-byte sectors, so this single fixed test
     * doesn't need to know the device's actual sector size up front.
     */
    private static final int TEST_LENGTH_BYTES = 4096;

    private RawDiskSpike() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Package-private so the flow can be exercised without a real {@code System.exit}. */
    static int run(String[] args) {
        Map<String, String> options = parseArgs(args);
        String target = options.get("target");
        String confirm = options.get("confirm");

        if (target == null || target.isBlank() || confirm == null || confirm.isBlank()) {
            log.error("Usage: RawDiskSpike --target=\\\\.\\PhysicalDriveN --confirm=<the disk's actual serial "
                    + "number>. Both are required; neither is ever inferred or defaulted.");
            return 2;
        }

        OptionalInt parsed = parseDiskNumber(target);
        if (parsed.isEmpty()) {
            log.error("--target must look exactly like \\\\.\\PhysicalDriveN (a literal disk number); got '{}'",
                    target);
            return 2;
        }
        int diskNumber = parsed.getAsInt();

        if (diskNumber == 0) {
            log.error("Refusing PhysicalDrive0 unconditionally - this holds even if it somehow isn't reported "
                    + "as the system disk.");
            return 3;
        }

        PowerShellQueryService powerShellQueryService = new PowerShellQueryService();
        SystemDiskResolver systemDiskResolver = new SystemDiskResolver(powerShellQueryService);
        DeviceDiscoveryService deviceDiscoveryService =
                new DeviceDiscoveryService(powerShellQueryService, systemDiskResolver);
        // allowLiveMode=true here is not a config bypass - invoking this program at all, with an
        // explicit --target and --confirm, from a terminal, already is the explicit live-mode action.
        SafetyValidator safetyValidator = new SafetyValidator(deviceDiscoveryService, new ZeroWipeProperties(true));

        PhysicalDevice device;
        try {
            device = safetyValidator.requireExistingDevice(diskNumber);
            safetyValidator.requireNotSystemDisk(device);
            safetyValidator.requireSerialConfirmed(device, confirm);
        } catch (SafetyCheckException e) {
            log.error("Safety check failed: {}", e.getMessage());
            return 3;
        }

        if (device.capacityBytes() <= 0 || device.capacityBytes() > CAPACITY_CEILING_BYTES) {
            log.error(
                    "Disk {} capacity ({} bytes) is outside the Phase 1 spike's ~16 GiB ceiling. This phase is "
                            + "for a disposable test VHD/USB only - refusing to touch what looks like a real drive.",
                    diskNumber,
                    device.capacityBytes());
            return 3;
        }

        log.info(
                "Safety checks passed for disk {} ({}, serial {}, {} bytes). Writing {} bytes at offset 0, "
                        + "flushing, reading back, and comparing.",
                diskNumber,
                device.model(),
                device.serialNumber(),
                device.capacityBytes(),
                TEST_LENGTH_BYTES);

        try {
            boolean verified = writeFlushReadBackVerify(target);
            if (verified) {
                log.info(
                        "PASS: wrote {} bytes at offset 0 on disk {}, flushed, read them back, and they matched "
                                + "byte-for-byte.",
                        TEST_LENGTH_BYTES,
                        diskNumber);
                return 0;
            }
            log.error("FAIL: bytes read back from disk {} did not match what was written.", diskNumber);
            return 4;
        } catch (RuntimeException e) {
            log.error("FAIL: raw disk operation on {} threw: {}", target, e.getMessage(), e);
            return 4;
        }
    }

    private static boolean writeFlushReadBackVerify(String path) {
        WinNT.HANDLE handle = openForReadWrite(path);
        try {
            // An ascending byte ramp, not all-zero and not random: a fixed, deterministic pattern
            // that can't accidentally "pass" against already-blank media, and is reproducible
            // (re-running the spike expects exactly the same bytes) rather than different every run.
            byte[] toWrite = new byte[TEST_LENGTH_BYTES];
            for (int i = 0; i < toWrite.length; i++) {
                toWrite[i] = (byte) i;
            }

            writeAt(handle, 0, toWrite);

            if (!Kernel32.INSTANCE.FlushFileBuffers(handle)) {
                int error = Kernel32.INSTANCE.GetLastError();
                throw new IllegalStateException("FlushFileBuffers failed for " + path + " (Win32 error " + error + ")");
            }

            byte[] readBack = readAt(handle, 0, toWrite.length);
            return Arrays.equals(toWrite, readBack);
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    private static WinNT.HANDLE openForReadWrite(String path) {
        WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(
                path,
                WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                null,
                WinNT.OPEN_EXISTING,
                0,
                null);
        if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new IllegalStateException("CreateFile failed to open " + path + " (Win32 error " + error + ")");
        }
        return handle;
    }

    private static void writeAt(WinNT.HANDLE handle, long offset, byte[] data) {
        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.Offset = (int) (offset & 0xFFFFFFFFL);
        overlapped.OffsetHigh = (int) (offset >>> 32);

        IntByReference bytesWritten = new IntByReference();
        boolean ok = Kernel32.INSTANCE.WriteFile(handle, data, data.length, bytesWritten, overlapped);
        if (!ok) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new IllegalStateException("WriteFile failed at offset " + offset + " (Win32 error " + error + ")");
        }
        if (bytesWritten.getValue() != data.length) {
            throw new IllegalStateException(
                    "WriteFile wrote " + bytesWritten.getValue() + " of " + data.length + " requested bytes at "
                            + "offset " + offset);
        }
    }

    private static byte[] readAt(WinNT.HANDLE handle, long offset, int length) {
        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.Offset = (int) (offset & 0xFFFFFFFFL);
        overlapped.OffsetHigh = (int) (offset >>> 32);

        byte[] buffer = new byte[length];
        IntByReference bytesRead = new IntByReference();
        boolean ok = Kernel32.INSTANCE.ReadFile(handle, buffer, length, bytesRead, overlapped);
        if (!ok) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new IllegalStateException("ReadFile failed at offset " + offset + " (Win32 error " + error + ")");
        }
        if (bytesRead.getValue() != length) {
            throw new IllegalStateException(
                    "ReadFile returned " + bytesRead.getValue() + " of " + length + " requested bytes at offset "
                            + offset);
        }
        return buffer;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 2) {
                    options.put(arg.substring(2, eq), arg.substring(eq + 1));
                }
            }
        }
        return options;
    }

    /**
     * Extracts the disk number from a {@code --target} value, requiring an
     * exact {@code \\.\PhysicalDriveN} match - no partial prefixes, no
     * trailing characters after the digits, no negative numbers. Package
     * visible so this parsing (easy to get subtly wrong with a hand-rolled
     * regex) is directly unit tested rather than trusted by inspection.
     */
    static OptionalInt parseDiskNumber(String target) {
        if (target == null || !target.startsWith(PHYSICAL_DRIVE_PREFIX)) {
            return OptionalInt.empty();
        }
        String digits = target.substring(PHYSICAL_DRIVE_PREFIX.length());
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
