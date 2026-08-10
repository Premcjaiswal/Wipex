package com.zerowipe.policy;

/**
 * Sanitization methods this system can select, each mapped to the single
 * NIST SP 800-88 assurance level it achieves. {@link #PHYSICAL_DESTRUCTION}
 * is a documentation workflow only - the engine never executes it.
 */
public enum SanitizationMethod {
    ZERO_FILL(NistCategory.CLEAR),
    DOD_3PASS_LEGACY(NistCategory.CLEAR),
    PHYSICAL_DESTRUCTION(NistCategory.DESTROY);

    private final NistCategory nistCategory;

    SanitizationMethod(NistCategory nistCategory) {
        this.nistCategory = nistCategory;
    }

    public NistCategory nistCategory() {
        return nistCategory;
    }
}
