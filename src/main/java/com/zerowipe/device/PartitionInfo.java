package com.zerowipe.device;

/**
 * Parsed row from {@code Get-Partition | Select-Object DiskNumber |
 * ConvertTo-Json}, used as the PowerShell fallback for system disk
 * resolution when the native volume-disk-extents call fails.
 */
record PartitionInfo(Integer diskNumber) {
}
