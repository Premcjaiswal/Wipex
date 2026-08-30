package com.zerowipe.sanitize;

/**
 * The three sanitization methods this system offers (screen 2), each
 * mapped to the NIST SP 800-88 Rev. 1 assurance level it achieves.
 *
 * <p>{@code DOD_3PASS_LEGACY} is exactly that - legacy and comparative
 * only. The DoD 5220.22-M overwrite matrix was removed from the NISPOM in
 * 2007; nothing in this system claims official DoD certification for it.
 *
 * <p>{@code CRYPTO_ERASE} is mapped to {@code PURGE} because that is what
 * NIST SP 800-88 itself calls a genuine cryptographic erase - but this
 * system must never claim Purge was achieved unless a real, practical
 * crypto erase was actually performed. When it isn't supported for a
 * device, the report says so plainly rather than pretending.
 */
public enum EraseMethod {
    ZERO_FILL(NistCategory.CLEAR),
    DOD_3PASS_LEGACY(NistCategory.CLEAR),
    CRYPTO_ERASE(NistCategory.PURGE);

    private final NistCategory nistCategory;

    EraseMethod(NistCategory nistCategory) {
        this.nistCategory = nistCategory;
    }

    public NistCategory nistCategory() {
        return nistCategory;
    }

    /**
     * NIST SP 800-88 Rev. 1 assurance levels - alternative levels selected
     * by media type and required confidentiality, not sequential phases.
     * {@code DESTROY} has no corresponding method in this system; it is
     * kept only as the reference concept the report/UI can point to when
     * neither Clear nor Purge is sufficient (physically destroy the
     * media - out of this tool's scope).
     */
    public enum NistCategory {
        CLEAR,
        PURGE,
        DESTROY
    }
}
