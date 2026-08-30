package com.zerowipe.device;

/**
 * Storage media type, identified via seek penalty plus PowerShell
 * cross-check. UNKNOWN means the media type could not be determined and
 * must not be treated as a default.
 */
public enum MediaType {
    HDD,
    SSD,
    NVME,
    UNKNOWN
}
