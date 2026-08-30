# CLAUDE.md — Project Context (Simplified Scope)

## What this project is

A Windows desktop application for **secure data sanitization** of internal HDDs
and SSDs, built as a solo final-year academic project. It detects storage
devices, walks the operator through a safety-confirmed sanitization run,
verifies the result with measured coverage, and produces an auditable
sanitization report.

**Project name:** ZeroWipe
**Target OS:** Windows 10/11 x64 only. No Linux or macOS code paths.
**Scale:** Solo student project, deliberately kept small enough for one
person to explain every line of it in a viva. Prefer clear, testable,
defensible code over feature count.

This project was deliberately re-scoped down from an earlier, more
elaborate design (a full NIST policy-decision engine, hand-rolled native
IOCTL device-property structs, SHA-256 hash-chained audit records). That
earlier design was correct but over-engineered for a solo build. This
document describes the simplified architecture that replaced it - do not
reintroduce that complexity without being asked.

## Development philosophy — enforce this strictly

```
SIMPLE -> WORKING -> SAFE -> VERIFIED -> DEMONSTRABLE
```

If a feature is not in the In Scope list below, do not build it, do not stub it
elaborately, and do not add abstractions "in preparation" for it beyond the one
plugin interface described under Extensibility.

## In scope

1. Windows 10/11 x64, internal HDD and SSD/NVMe storage
2. Storage device detection via PowerShell (`Get-PhysicalDisk`, `Get-Partition`)
   - model, serial number, capacity, bus type, media type, internal/removable
3. NIST SP 800-88 Clear/Purge/Destroy as the compliance/reference framework
4. **Simulation mode as the default**; live mode gated by explicit config
   plus every backend safety check passing
5. Zero Fill overwrite
6. DoD-style **legacy** 3-pass overwrite (never claimed as official/current
   DoD certification)
7. Cryptographic erase, but only when genuinely, practically supported for
   the selected device - never faked
8. Verification of the performed operation: FULL for small/test disks,
   SAMPLED for large disks, always labelled explicitly in the report
9. Basic SQLite operation history - simple and flat, not an enterprise
   audit architecture
10. PDF and JSON sanitization reports
11. The 6-screen Angular flow: Select Drive -> Select Method -> Safety
    Confirmation -> Sanitization Progress -> Verification -> Result/Report

## Explicitly OUT of scope — do not implement

- NVMe Format NVM / Sanitize commands, ATA Secure Erase, Opal / TCG PSID
  Revert - this is exactly *why* cryptographic erase is capability-gated
  to "not available" for most real devices in this build, rather than
  faked
- BitLocker cryptographic erase
- Digital signature / PKI on reports (plain SQLite history only)
- Bootable ISO / WinPE live boot environments
- jpackage / MSI installers / WiX - `java -jar` from an elevated terminal
  is the delivery mechanism
- Linux or macOS support
- Multi-user, authentication, cloud sync, network features, microservices,
  multiple privilege-helper services
- Complicated plugin systems or extra design patterns beyond the one
  `SanitizationMethod` interface
- AI/ML features
- Gutmann 35-pass - obsolete, debunked; do not implement, mention in docs only

If asked to add any of these, decline and remind the user they are out of
scope for this build.

## Core thesis (drives every design decision)

Most student implementations of this idea are technically wrong. They treat
NIST SP 800-88's Clear / Purge / Destroy as sequential phases, promote
deprecated methods like Gutmann 35-pass, and issue "compliance certificates"
for operations that are not compliant.

This project models the standard **correctly** and states its own limits:

- Clear, Purge, and Destroy are **alternative assurance levels** selected by
  media type and required confidentiality — NOT a pipeline.
- Zero Fill and the legacy DoD 3-pass overwrite achieve **Clear only**.
- Cryptographic erase achieves **Purge**, but only when genuinely,
  practically implementable for the selected device without the
  ATA/NVMe/Opal passthrough work this build deliberately excludes. Where
  it isn't practical, the system says so plainly - "Cryptographic erase is
  not available for this device" - it never fakes a Purge-level result.
- Verification reports the method used and **measured coverage**, labelled
  `FULL` or `SAMPLED` — never a bare "100% sanitized".
- A **cancelled** run states plainly that the disk may be partially
  sanitized. It never implies success.

When "looks impressive" conflicts with "is technically honest", choose honest.

## Compliance frameworks

| Framework | Role |
|---|---|
| **NIST SP 800-88 Rev. 1** | Primary. Clear/Purge/Destroy model. |
| **IEEE 2883-2022** | Cited in documentation as the modern successor with better SSD coverage. No code impact. |
| **DoD 5220.22-M** | Legacy/comparative only. The overwrite matrix was removed from the NISPOM in 2007; the UI must say so. |
| **Gutmann 35-pass** | DO NOT IMPLEMENT. Obsolete. Mention in docs as debunked only. |

## Tech stack (fixed — do not substitute)

```
Language          Java 21
Framework         Spring Boot 3.x, bound to 127.0.0.1:8088 ONLY
Device detection  PowerShell (Get-PhysicalDisk, Get-Partition) - no native
                  IOCTL device-property queries; see "Native access" below
Native access     JNA 5.x, narrowly - raw disk open/read/write only, for
                  the sanitize/verify write-and-read-back path
Frontend          Angular 20 + TypeScript + Bootstrap 5
Serving           Angular build output into src/main/resources/static
Live progress     Server-Sent Events (SseEmitter)
Database          SQLite via sqlite-jdbc + Spring Data JPA
PDF               openhtmltopdf
Build             Maven + frontend-maven-plugin (single `mvn package`)
Run               Executable JAR, launched from an elevated terminal
Testing           JUnit 5 + Mockito; VHD virtual disks for integration tests
```

No jpackage, no installer, no code signing. `java -jar zerowipe.jar` from an
Administrator terminal is the delivery mechanism.

## Package layout

```
com.zerowipe
├── device    detection: PowerShellQueryService, SystemDiskResolver,
│             DeviceDiscoveryService, PhysicalDevice, BusType, MediaType
├── safety    the backend's independent safety gate: SafetyValidator,
│             SafetyCheckException
├── sanitize  the erase method contract, its implementations, raw disk
│             access, and the job state machine
├── verify    VerificationResult, VerificationStrategy (FULL_READBACK /
│             SAMPLED), VerificationResultRepository
├── report    PDF + JSON sanitization report generation
├── audit     basic SQLite operation history
├── api       thin REST controllers only - no business logic
└── config    ZeroWipeProperties (zerowipe.* settings)
```

There is no `policy`, `job`, `nativelayer`, `engine`, or `common` package.
Earlier phases had a `policy` package (a full NIST decision engine with a
`PolicyDecision` result type and a `RefusalReason` enum) and a standalone
`nativelayer` package (hand-rolled JNA structs mirroring
`STORAGE_DEVICE_DESCRIPTOR`/`STORAGE_ADAPTER_DESCRIPTOR` byte-for-byte).
Both were removed as over-engineered for this project's actual needs - do
not reintroduce either without being asked.

## Architecture

```
Angular UI (127.0.0.1:8088)
  Select Drive -> Select Method -> Safety Confirmation -> Progress -> Verification -> Result/Report
        |
   REST + SSE
        |
Spring Boot
  device/
    PowerShellQueryService       runs Get-PhysicalDisk / Get-Partition, parses JSON
    SystemDiskResolver           resolves %SystemRoot% to a disk number
    DeviceDiscoveryService       builds PhysicalDevice records directly from PowerShell
  safety/
    SafetyValidator              system disk, live mode, device exists, serial
                                  confirmed, one job at a time - independent of the UI
  sanitize/
    EraseMethod                  ZERO_FILL / DOD_3PASS_LEGACY / CRYPTO_ERASE,
                                  each mapped to a NIST assurance level
    SanitizationMethod           the one plugin interface               (not yet built)
      ZeroFillSanitizer / DodThreePassSanitizer / CryptoEraseSanitizer  (not yet built)
    RawDiskAccess                 narrow raw disk I/O                    (not yet built)
    SanitizationJob, JobState, SanitizationMode, SanitizationJobRepository
    SanitizationJobOrchestrator   single-job-at-a-time state machine     (not yet built)
  verify/
    VerificationResult, VerificationStrategy, VerificationResultRepository
    VerificationService                                                  (not yet built)
  report/
    ReportService                 PDF + JSON report                      (not yet built)
  audit/
    AuditRecord, AuditRecordRepository - target design is a flat history
    log (id, timestamp, eventType, jobId, payload); the entity currently
    still carries a SHA-256 hash chain left over from before this
    simplification and should lose those fields the next time this
    package is touched
  api/
    DeviceController (built); SanitizationController, ReportController  (not yet built)
        |
   JNA (raw disk I/O ONLY - never used for detection)
        |
Windows Storage Stack -> Physical Drives
```

## Extensibility (the ONLY future-proofing to build)

One interface, nothing more. Do not confuse this with `EraseMethod` (the
enum that just labels which method was selected) - `SanitizationMethod` is
the interface that actually does the work:

```java
public interface SanitizationMethod {
    EraseMethod method();
    boolean isSupported(PhysicalDevice device);
    void execute(SanitizationJob job, ProgressCallback callback) throws SanitizationException;
}
```

Implementations are Spring beans: `ZeroFillSanitizer`, `DodThreePassSanitizer`,
`CryptoEraseSanitizer`. Adding a future method means adding one new
implementation, not redesigning the application. This is a starting
sketch - exact signatures may be refined once the orchestrator and
progress-reporting mechanism are actually built, but the one-interface,
many-implementations shape is fixed. Do not build any other extension
machinery.

## Native access approach

Device detection (screen 1's model/serial/capacity/bus type/media type,
and system-disk resolution) is done **entirely through PowerShell** -
`Get-PhysicalDisk` and `Get-Partition`, parsed as JSON. There is no native
IOCTL device-property layer in this build. An earlier version had one
(hand-rolled `STORAGE_DEVICE_DESCRIPTOR`/`STORAGE_ADAPTER_DESCRIPTOR` JNA
structs, byte-offset string parsing, a `STORAGE_BUS_TYPE` mapping table)
and it was removed - PowerShell gives the same information more simply and
more reliably, with none of the struct-layout risk described below.

JNA is used for exactly one thing: raw physical-disk I/O for the actual
sanitization write and the verification read-back. This is narrow by
design - `CreateFile`, `ReadFile`, `WriteFile`, `CloseHandle`, using the
`OVERLAPPED` `Offset`/`OffsetHigh` technique for positioned reads/writes
without needing `SetFilePointerEx` (which JNA's bundled `Kernel32`
interface doesn't expose). No `STORAGE_*` IOCTL structs are needed for
this path.

**Before this is wired into the real application, prove it as an
isolated, standalone spike** (not a Spring bean, not reachable via HTTP) -
see "Phase 1 spike" below. Do not assume any part of it is correct until
it has actually been run against real Windows.

### Known native gotchas — respect these

1. **Sector alignment.** Raw disk reads/writes require both offset AND
   length to be exact multiples of `BytesPerSector`. Unaligned calls fail.
2. **Volume locking.** Windows blocks raw writes to a disk with a mounted,
   in-use volume. A target VHD created unformatted sidesteps this for the
   Phase 1 spike; a real target with a filesystem needs
   `FSCTL_LOCK_VOLUME`/`FSCTL_DISMOUNT_VOLUME`, which is not yet
   implemented - don't pretend this problem doesn't exist.
3. **JNA `Structure` subclasses must be `public`.** A `public` field on a
   non-public class still fails `Field.get()` with `IllegalAccessException`
   - JNA's own reflection code doesn't call `setAccessible()` for a field
   it assumes is already reachable because it's marked `public`. This
   exact bug shipped once in an earlier phase and was only caught by
   running on real Windows; the failure mode on a non-Windows dev machine
   (missing `kernel32.dll`) happens earlier and hides it completely.
4. **If a struct layout or a PowerShell field mapping is uncertain, say so
   rather than guessing.** A wrong native offset can crash the JVM; a
   wrong PowerShell property name just silently returns null forever with
   nothing to explain why. Both have been real risks in this project -
   document the uncertainty rather than asserting confidence you don't have.
5. **`isRemovable` is approximated as `busType == USB`.**
   `Get-PhysicalDisk` has no direct removable-media flag the way the old
   native descriptor did. Worth confirming against a non-USB removable
   drive if one is ever available.

### Phase 1 spike (raw disk access)

Before building the real sanitization engine, prove the technical approach
in total isolation:

- A standalone `main()` class, e.g. `com.zerowipe.sanitize.spike.RawDiskSpike`
  - not a Spring bean, not reachable over HTTP, invoked manually from a
  terminal with an explicit `--target=\\.\PhysicalDriveN` argument. Never
  auto-select or hard-code a target.
- Hard-refuse `PhysicalDrive0` and whatever `SystemDiskResolver` reports as
  the system disk, in code, regardless of what was passed in.
- Require serial-number confirmation before any write, same principle as
  screen 3.
- A capacity ceiling (roughly 8-16 GB) so a disposable VHD/USB is expected
  and a large real drive is refused outright.
- The whole flow: validate target -> validate safety conditions -> open
  target -> write 4 KB at offset 0 -> flush -> read back 4 KB -> byte-for-byte
  compare -> exit. No loop, no full wipe, no UI, no job orchestration, no
  reports.
- Test order: unformatted/unpartitioned VHD first (`New-VHD` + `Mount-VHD`
  - no mounted volume means no locking complexity yet), a dedicated
  disposable USB second, never the system disk, never a disk with data
  that matters.

## Safety requirements (NON-NEGOTIABLE)

1. **Simulation mode is the default everywhere.** Live mode requires
   `zerowipe.allow-live-mode: true` in config AND every check below to pass.
2. **The backend independently validates every live sanitization request**
   (`safety.SafetyValidator`) - never trust the frontend alone:
   - no other sanitization job is already running
   - live mode is enabled in config
   - the target disk actually exists (looked up fresh, not from a cached
     selection)
   - the target is not the system disk
   - the operator's typed serial number matches the target's actual
     current serial number exactly - "yes" is not acceptable, and
     re-checking against a freshly looked-up device is what catches the
     device having been swapped since it was selected
3. **Never auto-select a destructive method.**
4. **Log intent before execution.** Write the audit record before the
   first destructive byte.
5. **Only one sanitization job may run at a time**; navigation/actions
   that could corrupt a running job are disabled while it's active.
6. **Cancellation is honest, not silent.** If a job is cancelled
   mid-write, the result explicitly says the disk may be partially
   sanitized - never implies success.

## Honesty requirements in output

- Never print "100% sanitized". The report states the verification type
  (`FULL` or `SAMPLED`) and the measured coverage explicitly.
- Cryptographic erase must never be faked. If it isn't genuinely practical
  for the selected device, the UI says exactly that: "Cryptographic erase
  is not available for this device."
- The DoD-style 3-pass method is always labelled "DoD-style legacy 3-pass
  overwrite" - never claimed as official/current DoD certification.
- A cancelled job's result says the disk may be partially sanitized, never
  that the operation succeeded.

## Code conventions

- Package root: `com.zerowipe`
- Constructor injection only; no field `@Autowired`
- Records for DTOs, enums for methods (`EraseMethod`)
- No `System.out.println` — use SLF4J
- Safety failures throw `safety.SafetyCheckException` with a plain,
  human-readable message - no `RefusalReason`-style enum of reasons; a
  message is enough for this project's size
- Native calls: check return codes, release handles in `finally`, and
  remember every JNA `Structure` subclass must be `public`
- Javadoc on public methods in the `safety` and `sanitize` packages

## Testing strategy

- **Unit (bulk of effort):** `safety.SafetyValidator` is the most
  safety-critical class in the codebase and should have the highest test
  coverage - every guard check, and the ordering between them, needs to be
  locked in. PowerShell JSON parsing
  (`PowerShellQueryService.parseJsonArray`) is tested directly against
  sample `ConvertTo-Json` output rather than by spawning a real process.
- **Integration:** VHD virtual disks created via `New-VHD`. Windows
  exposes these as real `\\.\PhysicalDriveN`, so the sanitizer/verification
  code can be tested for real, safely, repeatably - this is also how the
  Phase 1 spike itself gets proven.
- **Manual:** one spare/disposable physical drive, only after VHD tests
  pass, per the testing order in "Phase 1 spike" above.

## Working agreement

- Build in the phases given. Do not jump ahead or add out-of-scope features.
- Small, independently compilable increments - compile and run the test
  suite after every change, not just at the end of a phase.
- After each phase, stop and summarise what was built and what should be
  verified manually before continuing.
- Ask before adding any dependency not listed in the stack.
- Prefer fewer, clearer classes over elaborate layering. If a change
  breaks something else, fix it forward in the same step rather than
  leaving the tree broken.
