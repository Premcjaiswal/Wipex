package com.zerowipe.policy;

import java.util.List;
import java.util.Map;

/**
 * The policy engine's output for a single device: which method it
 * recommends, which methods are permitted or refused (with reasons), the
 * highest NIST assurance level actually achievable, and any warnings that
 * must accompany the result regardless of which method is chosen.
 */
public record PolicyDecision(
        SanitizationMethod recommendedMethod,
        List<SanitizationMethod> permittedMethods,
        Map<SanitizationMethod, RefusalReason> refusedMethods,
        NistCategory achievableAssuranceLevel,
        List<String> warnings,
        String rationale) {
}
