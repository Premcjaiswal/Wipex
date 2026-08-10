# CLAUDE.md — Project Context (MVP Scope)

## What this project is

A Windows desktop application for **secure data sanitization** of internal HDDs
and SSDs, built as a solo final-year academic project. It detects storage
devices, determines which sanitization methods are honestly valid for each
device, executes overwrite-based sanitization, verifies the result with measured
coverage, and produces an auditable sanitization report.

**Project name:** ZeroWipe (change if desired)
**Target OS:** Windows 10/11 x64 only. No Linux or macOS code paths.
**Scale:** Solo student project. Prefer clear, testable, defensible code over
feature count.

## Development philosophy — enforce this strictly

```
SIMPLE -> WORKING -> TESTED -> VERIFIED -> (advanced features later)
```

If a feature is not in the In Scope list below, do not build it, do not stub it
elaborately, and do not add abstractions "in preparation" for it beyond the one
plugin interface described under Extensibility.

## In scope (MVP)

1. Windows 10/11
2. Internal HDD and SSD/NVMe storage
3. Storage device detection + HDD vs SSD/NVMe identification
4. NIST SP 800-88 based sanitization method selection
5. **Simulation mode as the default**
6. Zero Fill overwrite
7. DoD-style multi-pass overwrite (legacy/comparative, mainly for HDD)
8. Verification of the performed operation, with measured coverage
9. Basic audit logging (SHA-256 hash chain)
10. Basic sanitization report (HTML + PDF)

## Explicitly OUT of scope — do not implement

- NVMe Format NVM / Sanitize commands
- ATA Secure Erase
- Opal / TCG PSID Revert
- BitLocker cryptographic erase
- Digital signature / PKI on certificates (hash chain only)
- jpackage / MSI installers / WiX
- Live boot (WinPE) environments
- Linux or macOS support
- Multi-user, authentication, cloud sync, network features

If asked to add any of these, decline and remind me they are post-MVP.

## Core thesis (drives every design decision)

Most student implementations of this idea are technically wrong. They treat
NIST SP 800-88's Clear / Purge / Destroy as sequential phases, promote
deprecated methods like Gutmann 35-pass, and issue "compliance certificates"
for operations that are not compliant.

This project models the standard **correctly** and states its own limits:

- Clear, Purge, and Destroy are **alternative assurance levels** selected by
  media type and required confidentiality — NOT a pipeline.
- **This system achieves Clear only.** It cannot achieve Purge, because Purge
  requires device-level commands that are out of scope. Where Purge is required,
  the system **refuses and explains why**.
- Verification reports the method used and **measured coverage percentage**,
  never a bare "100% sanitized".

When "looks impressive" conflicts with "is technically honest", choose honest.

## Compliance frameworks

| Framework | Role |
|---|---|
| **NIST SP 800-88 Rev. 1** | Primary. Clear/Purge/Destroy model. Report fields based on Appendix A. |
| **IEEE 2883-2022** | Cited in documentation as the modern successor with better SSD coverage. No code impact. |
| **DoD 5220.22-M** | Legacy/comparative only. Implemented and benchmarked, labelled "Legacy — superseded" in the UI. The overwrite matrix was removed from the NISPOM in 2007. |
| **Gutmann 35-pass** | DO NOT IMPLEMENT. Obsolete. Mention in docs as debunked only. |

## Tech stack (fixed — do not substitute)

```
Language       Java 21 (virtual threads for long-running wipe jobs)
Framework      Spring Boot 3.x, bound to 127.0.0.1 ONLY
Native access  JNA 5.x (com.sun.jna.platform.win32)
Frontend       Angular 20 + TypeScript + Bootstrap 5
Serving        Angular build output into src/main/resources/static
Live progress  Server-Sent Events (SseEmitter)
Database       SQLite via sqlite-jdbc + Spring Data JPA
PDF            openhtmltopdf
Hashing        SHA-256 via java.security.MessageDigest (no BouncyCastle)
Build          Maven + frontend-maven-plugin (single `mvn package`)
Run            Executable JAR, launched from an elevated terminal
Testing        JUnit 5 + Mockito; VHDX virtual disks for integration tests
```

No jpackage, no installer, no code signing. `java -jar zerowipe.jar` from an
Administrator terminal is the delivery mechanism for the MVP.

## Architecture

```
Angular UI (localhost)
  Devices -> Method Selection -> Pre-flight -> Live Progress -> Report -> Audit
        |
   REST + SSE
        |
Spring Boot
  |- DeviceDiscoveryService     enumerate + identify physical drives
  |- CapabilityDetector         media type, TRIM, SMART, bus type
  |- SanitizationPolicyEngine   NIST decision logic  <-- INTELLECTUAL CORE
  |- JobOrchestrator            job state machine, persisted
  |- SanitizationEngine         driver plugin interface:
  |     |- SimulationDriver         (DEFAULT)
  |     |- OverwriteDriver          (zero fill, DoD 3-pass)
  |- VerificationService        full read-back or sampled, records coverage %
  |- AuditLogService            SHA-256 hash-chained records
  |- ReportService              HTML/PDF sanitization report
        |
   JNA / DeviceIoControl
        |
Windows Storage Stack -> Physical Drives
```

## Extensibility (the ONLY future-proofing to build)

One interface, nothing more:

```java
public interface SanitizationDriver {
    SanitizationMethod method();
    boolean supports(PhysicalDevice device, DeviceCapabilities caps);
    void prepare(SanitizationJob job) throws SanitizationException;
    void execute(SanitizationJob job, ProgressCallback cb) throws SanitizationException;
    void cleanup(SanitizationJob job);
}
```

Drivers are discovered as Spring beans. Adding NVMe Sanitize later means adding
one class. Do not build any other extension machinery.

## Critical rule: the native boundary

**ALL** native calls go behind a single interface, `NativeDeviceGateway`.
Nothing above that layer touches JNA. This makes the whole application
unit-testable with a mock, and it is the most important structural decision
in the codebase.

```java
public interface NativeDeviceGateway {
    List<Integer> enumerateDiskNumbers();
    DeviceDescriptor queryDeviceProperty(int diskNumber);
    SeekPenaltyInfo querySeekPenalty(int diskNumber);
    TrimInfo queryTrimSupport(int diskNumber);
    AdapterDescriptor queryAdapter(int diskNumber);
    DriveGeometry queryGeometry(int diskNumber);
    VolumeLockHandles lockAndDismountVolumes(int diskNumber);
    void writeSectors(int diskNumber, long offset, byte[] buffer);
    byte[] readSectors(int diskNumber, long offset, int length);
}
```

Note: no ATA/NVMe/SCSI pass-through in the MVP. Media type comes from seek
penalty plus PowerShell, which is sufficient and far simpler.

## Windows native reference (MVP subset only)

| Purpose | Call |
|---|---|
| Open raw disk | `CreateFile("\\\\.\\PhysicalDriveN", GENERIC_READ\|GENERIC_WRITE)` |
| Device identity | `IOCTL_STORAGE_QUERY_PROPERTY` + `StorageDeviceProperty` |
| HDD vs SSD | `IOCTL_STORAGE_QUERY_PROPERTY` + `StorageDeviceSeekPenaltyProperty` |
| TRIM support | `StorageDeviceTrimProperty` |
| Bus type | `StorageAdapterProperty` |
| Capacity / sector size | `IOCTL_DISK_GET_DRIVE_GEOMETRY_EX` |
| Prepare for raw write | `FSCTL_LOCK_VOLUME` then `FSCTL_DISMOUNT_VOLUME` |
| SMART / media type cross-check | PowerShell `Get-PhysicalDisk`, `Get-StorageReliabilityCounter` |

### Known native gotchas — respect these

1. **Sector alignment.** Raw disk writes require both offset AND length to be
   exact multiples of `BytesPerSector` (512 or 4096). Unaligned calls fail.
2. **String offsets.** `STORAGE_DEVICE_DESCRIPTOR` returns byte offsets into the
   same buffer, not inline strings. Read the offset, then read the
   null-terminated ASCII at `buffer + offset`.
3. **Volume locking.** Windows blocks raw writes to disks with mounted volumes.
   Lock and dismount every volume; keep handles OPEN for the whole operation.
   Handle `ERROR_ACCESS_DENIED` with a clear message naming the volume.
4. **Buffer sizing.** Use `MaximumTransferLength` from the adapter descriptor.
5. **USB bus type** means a bridge chip; media type reporting is unreliable.
   Flag it in the UI.
6. **RAID bus type** means a virtual disk, not physical media. No sanitization
   claim is valid — refuse.
7. If a struct layout is uncertain, **say so rather than guessing**. Wrong
   offsets crash the JVM and are hard to debug.

## Safety requirements (NON-NEGOTIABLE)

Implement these before any write path exists.

1. **Simulation mode is the default.** Live mode requires BOTH an explicit flag
   in the job request AND `zerowipe.allow-live-mode: true` in config.
2. **Hard block:** physical drive 0, any disk containing the system volume, any
   disk containing the page file.
3. **Serial number confirmation.** The operator must type the target drive's
   serial number exactly. "yes" is not acceptable.
4. **Serial allowlist.** In live mode, the target serial must also appear in
   `zerowipe.test-disk-allowlist`.
5. **Pre-flight screen** before every job: target, method, NIST category,
   estimated duration, warnings, mode badge (SIMULATION green / LIVE red).
6. **Log intent before execution.** Write the audit record before the first
   destructive byte.
7. **Never auto-select** a destructive method.

## Honesty requirements in output

- Never print "100% sanitized". Print verification method and measured coverage.
- Capture SMART reallocated sector count before and after; state that remapped
  sectors are unreachable by overwriting.
- On SSD/NVMe, every overwrite result must carry a warning that wear levelling
  and over-provisioning leave unmapped blocks that overwriting cannot reach,
  so the result is **Clear, not Purge**.
- The audit chain is **tamper-evident, not tamper-proof**. Say so in the docs.

## Code conventions

- Package root: `com.zerowipe`
- Constructor injection only; no field `@Autowired`
- Records for DTOs, enums for methods and refusal reasons
- No `System.out.println` — use SLF4J
- Every refusal carries a `RefusalReason` enum + human-readable explanation
- Native calls: check return codes, release handles in `finally`
- Javadoc on public methods in the native and policy layers

## Testing strategy

- **Unit (bulk of effort):** mock `NativeDeviceGateway`, feed fabricated device
  descriptors, assert policy engine decisions. The policy engine should have the
  highest coverage in the codebase — it is the graded intellectual core.
- **Integration:** VHDX virtual disks created via `New-VHD`. Windows exposes
  these as real `\\.\PhysicalDriveN`, so the overwrite driver can be tested for
  real, safely, repeatably.
- **Manual:** one spare physical drive, only after VHDX tests pass.

## Working agreement

- Build in the phases given. Do not jump ahead or add out-of-scope features.
- After each phase, stop and summarise what was built and what I should verify
  manually before continuing.
- Ask before adding any dependency not listed in the stack.
- Prefer fewer, clearer classes over elaborate layering.
