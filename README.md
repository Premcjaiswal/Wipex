# ZeroWipe

> **⚠️ SAFETY WARNING**
>
> ZeroWipe performs **destructive, irreversible overwrite operations** on
> physical storage devices. Live mode permanently destroys all data on the
> target disk. Always confirm the target device, its serial number, and the
> current mode (SIMULATION vs LIVE) before proceeding.
>
> **Simulation mode is the default and performs no writes.** Live mode
> requires an explicit flag on the job request *and*
> `zerowipe.allow-live-mode: true` in configuration, plus operator
> confirmation of the exact serial number of the target disk. Never run this
> application against a disk you cannot afford to lose.
>
> This project is a solo final-year academic project. It has not been
> independently audited and carries no warranty of any kind. Do not use it
> against production data.

## What this is

A Windows desktop application for secure data sanitization of internal HDDs
and SSDs, implementing a NIST SP 800-88 Rev. 1 based sanitization workflow:
device detection, method selection, overwrite execution, verification with
measured coverage, and an auditable sanitization report.

**This system achieves Clear only.** It does not implement Purge-level
methods (NVMe Sanitize, ATA Secure Erase, Opal/PSID revert, BitLocker
crypto-erase). Where Purge is required, the system refuses and explains why.
See `CLAUDE.md` for full project scope and rationale.

**Target OS:** Windows 10/11 x64 only.

## Status

Project skeleton. No sanitization logic is implemented yet.

## Tech stack

- Java 21, Spring Boot 3.x, bound to `127.0.0.1` only
- JNA for native Windows storage APIs
- Angular 20 + Bootstrap 5 (served from `src/main/resources/static`)
- SQLite (via `sqlite-jdbc`) + Spring Data JPA
- SHA-256 hash-chained audit log
- HTML/PDF reports via openhtmltopdf

## Build

```
mvn package
```

This runs the Angular production build (output into
`src/main/resources/static`) before packaging the Spring Boot JAR.

## Run

Must be run from an **elevated (Administrator) terminal** on Windows for raw
disk access:

```
java -jar target/zerowipe.jar
```

Then open http://127.0.0.1:8080 in a browser.

## Configuration

See `src/main/resources/application.yml` for `zerowipe.*` settings,
including `zerowipe.allow-live-mode` (default `false`) and
`zerowipe.test-disk-allowlist`.
