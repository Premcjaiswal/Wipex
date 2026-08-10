package com.zerowipe.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SanitizationMethodTest {

    @Test
    void zeroFillAchievesClear() {
        assertEquals(NistCategory.CLEAR, SanitizationMethod.ZERO_FILL.nistCategory());
    }

    @Test
    void dod3PassLegacyAchievesClear() {
        assertEquals(NistCategory.CLEAR, SanitizationMethod.DOD_3PASS_LEGACY.nistCategory());
    }

    @Test
    void physicalDestructionAchievesDestroy() {
        assertEquals(NistCategory.DESTROY, SanitizationMethod.PHYSICAL_DESTRUCTION.nistCategory());
    }

    @Test
    void everyMethodMapsToANistCategory() {
        for (SanitizationMethod method : SanitizationMethod.values()) {
            assertNotNull(method.nistCategory(), method + " has no NIST category");
        }
    }
}
