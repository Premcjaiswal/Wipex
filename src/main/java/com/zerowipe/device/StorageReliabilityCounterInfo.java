package com.zerowipe.device;

/**
 * Parsed row from {@code Get-StorageReliabilityCounter | Select-Object
 * DeviceId, Wear, PowerOnHours | ConvertTo-Json}.
 *
 * <p><b>Reallocated sector count is deliberately not modelled here.</b>
 * {@code MSFT_StorageReliabilityCounter} (what {@code Get-StorageReliabilityCounter}
 * surfaces) is not confirmed to expose a reallocated-sector-count property
 * comparable to classic SMART attribute 5 - guessing a property name here
 * risks silently and permanently returning null forever on real hardware
 * with nothing to say why. {@link DeviceCapabilities#reallocatedSectorCount()}
 * is left unset by {@link CapabilityDetector} until this is verified
 * against a real Windows machine.
 */
record StorageReliabilityCounterInfo(Integer deviceId, Integer wear, Long powerOnHours) {
}
