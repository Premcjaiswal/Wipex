package com.zerowipe.device;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves which physical disk number(s) hold the volume {@code
 * %SystemRoot%} lives on, so {@link DeviceDiscoveryService} can mark them
 * {@code isSystemDisk = true}, via {@code Get-Partition}. If the system
 * volume spans more than one physical disk (a spanned/Storage Spaces
 * volume), every disk it spans is treated as a system disk - partial
 * protection would not be safe.
 *
 * <p>The result is resolved once and cached for the life of the JVM,
 * since the system volume's location does not change while this
 * application is running.
 */
@Service
public class SystemDiskResolver {

    private static final Logger log = LoggerFactory.getLogger(SystemDiskResolver.class);

    private final PowerShellQueryService powerShellQueryService;

    private volatile Set<Integer> cachedSystemDiskNumbers;

    public SystemDiskResolver(PowerShellQueryService powerShellQueryService) {
        this.powerShellQueryService = powerShellQueryService;
    }

    public synchronized Set<Integer> systemDiskNumbers() {
        if (cachedSystemDiskNumbers == null) {
            cachedSystemDiskNumbers = resolve();
        }
        return cachedSystemDiskNumbers;
    }

    private Set<Integer> resolve() {
        String driveLetterWithColon = driveLetterFromSystemRoot(System.getenv("SystemRoot"));
        if (driveLetterWithColon == null) {
            log.warn("Could not determine the system drive letter from %SystemRoot%; "
                    + "no disk will be marked as the system disk");
            return Set.of();
        }
        return resolveForDriveLetter(driveLetterWithColon);
    }

    /** Package-private so it's testable without faking environment variables. */
    Set<Integer> resolveForDriveLetter(String driveLetterWithColon) {
        String driveLetterOnly = driveLetterWithColon.substring(0, 1);
        List<Integer> diskNumbers = powerShellQueryService.queryDiskNumbersForDriveLetter(driveLetterOnly);
        if (diskNumbers.isEmpty()) {
            log.warn("Could not resolve the system disk via PowerShell for drive {}", driveLetterWithColon);
        } else if (diskNumbers.size() > 1) {
            log.warn(
                    "System volume {} spans {} physical disks {}; treating all of them as system disks",
                    driveLetterWithColon,
                    diskNumbers.size(),
                    diskNumbers);
        }
        return Set.copyOf(diskNumbers);
    }

    /** Package-private and pure so it's directly testable: {@code "C:\Windows"} -&gt; {@code "C:"}. */
    static String driveLetterFromSystemRoot(String systemRoot) {
        if (systemRoot == null || systemRoot.length() < 2 || systemRoot.charAt(1) != ':') {
            return null;
        }
        return systemRoot.substring(0, 2); // "C:"
    }
}
