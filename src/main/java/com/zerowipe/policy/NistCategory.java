package com.zerowipe.policy;

/**
 * NIST SP 800-88 Rev. 1 assurance levels. These are alternative assurance
 * levels selected by media type and required confidentiality, not
 * sequential phases.
 */
public enum NistCategory {
    CLEAR,
    PURGE,
    DESTROY
}
