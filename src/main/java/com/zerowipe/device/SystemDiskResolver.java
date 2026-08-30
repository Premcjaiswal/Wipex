package com.zerowipe.device;

import com.zerowipe.nativelayer.NativeAccessException;
import com.zerowipe.nativelayer.NativeDeviceGateway;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves which physical disk number(s) hold the volume {@code
 * %SystemRoot%} lives on, so {@link DeviceDiscoveryService} can mark them
 * {@code isSystemDisk = true}. Tries the native
 * {@code IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS} call first, falling back to
 * {@code Get-Partition} over PowerShell if that fails. If the system
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

    private final NativeDeviceGateway nativeDeviceGateway;
    private final PowerShellQueryService powerShellQueryService;

    private volatile Set<Integer> cachedSystemDiskNumbers;

    public SystemDiskResolver(NativeDeviceGateway nativeDeviceGateway, PowerShellQueryService powerShellQueryService) {
        this.nativeDeviceGateway = nativeDeviceGateway;
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

    /** Package-private so the native/PowerShell fallback chain is testable without faking environment variables. */
    Set<Integer> resolveForDriveLetter(String driveLetterWithColon) {
        try {
            List<Integer> extents = nativeDeviceGateway.queryVolumeDiskExtents(driveLetterWithColon);
            if (!extents.isEmpty()) {
                if (extents.size() > 1) {
                    log.warn(
                            "System volume {} spans {} physical disks {}; treating all of them as system disks",
                            driveLetterWithColon,
                            extents.size(),
                            extents);
                }
                return Set.copyOf(extents);
            }
        } catch (NativeAccessException e) {
            log.warn(
                    "Native system volume resolution failed for {}; falling back to PowerShell Get-Partition: {}",
                    driveLetterWithColon,
                    e.getMessage());
        }

        String driveLetterOnly = driveLetterWithColon.substring(0, 1);
        List<Integer> fromPowerShell = powerShellQueryService.queryDiskNumbersForDriveLetter(driveLetterOnly);
        if (fromPowerShell.isEmpty()) {
            log.warn("Could not resolve the system disk via native call or PowerShell for drive {}", driveLetterWithColon);
        }
        return Set.copyOf(fromPowerShell);
    }

    /** Package-private and pure so it's directly testable: {@code "C:\Windows"} -&gt; {@code "C:"}. */
    static String driveLetterFromSystemRoot(String systemRoot) {
        if (systemRoot == null || systemRoot.length() < 2 || systemRoot.charAt(1) != ':') {
            return null;
        }
        return systemRoot.substring(0, 2); // "C:"
    }
}
